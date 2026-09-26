import { Webhook } from "https://esm.sh/standardwebhooks@1.0.0";
import { buildAuthEmails, type AuthEmail } from "./email.ts";

type Config = {
  hookSecret: string;
  tenantId: string;
  clientId: string;
  clientSecret: string;
  sender: string;
  supabaseUrl: string;
};

let cachedToken: { value: string; expiresAt: number } | undefined;

function config(): Config {
  const required = (name: string) => {
    const result = Deno.env.get(name);
    if (!result) throw new Error(`Missing ${name}`);
    return result;
  };
  const hookSecret = required("SEND_EMAIL_HOOK_SECRET");
  if (!/^v1,whsec_[A-Za-z0-9+/=]+$/.test(hookSecret)) throw new Error("Invalid hook secret");
  const tenantId = required("MS_TENANT_ID");
  const clientId = required("MS_CLIENT_ID");
  if (!/^[a-f0-9-]{36}$/i.test(tenantId) || !/^[a-f0-9-]{36}$/i.test(clientId)) {
    throw new Error("Invalid Microsoft application configuration");
  }
  return {
    hookSecret: hookSecret.replace("v1,whsec_", ""),
    tenantId, clientId,
    clientSecret: required("MS_CLIENT_SECRET"),
    sender: "payroll@unionorth.com",
    supabaseUrl: required("SUPABASE_URL"),
  };
}

async function graphToken(settings: Config): Promise<string> {
  if (cachedToken && cachedToken.expiresAt > Date.now() + 60_000) return cachedToken.value;
  const form = new URLSearchParams({
    client_id: settings.clientId,
    client_secret: settings.clientSecret,
    scope: "https://graph.microsoft.com/.default",
    grant_type: "client_credentials",
  });
  const response = await fetch(`https://login.microsoftonline.com/${settings.tenantId}/oauth2/v2.0/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: form,
    signal: AbortSignal.timeout(2000),
  });
  if (!response.ok) {
    console.error("Microsoft token request failed", response.status);
    throw new Error("Microsoft authentication failed");
  }
  const token = await response.json();
  if (typeof token.access_token !== "string" || typeof token.expires_in !== "number") {
    throw new Error("Invalid Microsoft token response");
  }
  cachedToken = { value: token.access_token, expiresAt: Date.now() + token.expires_in * 1000 };
  return token.access_token;
}

async function send(settings: Config, accessToken: string, mail: AuthEmail): Promise<void> {
  const response = await fetch(`https://graph.microsoft.com/v1.0/users/${encodeURIComponent(settings.sender)}/sendMail`, {
    method: "POST",
    headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      message: {
        subject: mail.subject,
        body: { contentType: "Text", content: mail.content },
        toRecipients: [{ emailAddress: { address: mail.to } }],
      },
      saveToSentItems: true,
    }),
    signal: AbortSignal.timeout(2000),
  });
  if (response.status !== 202) {
    console.error("Microsoft Graph rejected auth email", response.status);
    throw new Error("Email provider rejected the message");
  }
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });
  if (Number(request.headers.get("content-length")) > 65536) {
    return new Response("Payload too large", { status: 413 });
  }
  let settings: Config;
  try {
    settings = config();
  } catch {
    console.error("Send email hook is not configured");
    return new Response("Service unavailable", { status: 503 });
  }
  let payload: unknown;
  try {
    const body = await request.text();
    if (body.length > 65536) return new Response("Payload too large", { status: 413 });
    const webhook = new Webhook(settings.hookSecret);
    payload = webhook.verify(body, Object.fromEntries(request.headers));
  } catch {
    return new Response("Unauthorized", { status: 401 });
  }
  try {
    const messages = buildAuthEmails(payload as Parameters<typeof buildAuthEmails>[0], settings.supabaseUrl);
    const token = await graphToken(settings);
    await Promise.all(messages.map((message) => send(settings, token, message)));
    return Response.json({});
  } catch (error) {
    console.error("Send email hook failed", error instanceof Error ? error.message : "Unknown error");
    return Response.json({ error: { http_code: 502, message: "Unable to send authentication email" } }, { status: 502 });
  }
});
