export type AuthEmail = { to: string; subject: string; content: string };

type HookPayload = {
  user?: { email?: unknown; new_email?: unknown };
  email_data?: {
    email_action_type?: unknown;
    token?: unknown;
    token_hash?: unknown;
    token_new?: unknown;
    token_hash_new?: unknown;
    redirect_to?: unknown;
    site_url?: unknown;
  };
};

function value(input: unknown): string {
  return typeof input === "string" ? input.trim() : "";
}

function email(input: unknown): string {
  const result = value(input);
  if (!/^[^\s@<>]+@[^\s@<>]+\.[^\s@<>]+$/.test(result) || result.length > 254) {
    throw new Error("Invalid email hook payload");
  }
  return result;
}

function redirect(data: NonNullable<HookPayload["email_data"]>): string {
  const destination = value(data.redirect_to) || value(data.site_url);
  const url = new URL(destination);
  if ((url.protocol !== "https:" && !(url.protocol === "http:" &&
      ["localhost", "127.0.0.1"].includes(url.hostname))) ||
      url.username || url.password || destination.length > 2048) {
    throw new Error("Invalid email hook redirect");
  }
  return url.href;
}

function verifyLink(baseUrl: string, hash: string, type: string, target: string): string {
  // Supabase prefixes hashes with pkce_ when the request uses the PKCE flow.
  if (!/^(?:pkce_)?[a-fA-F0-9]{32,128}$/.test(hash)) {
    throw new Error("Invalid email hook token");
  }
  const url = new URL("/auth/v1/verify", baseUrl);
  if (url.protocol !== "https:") throw new Error("Invalid Supabase URL");
  url.searchParams.set("token", hash);
  url.searchParams.set("type", type);
  url.searchParams.set("redirect_to", target);
  return url.href;
}

function linkedMail(to: string, subject: string, introduction: string, link: string): AuthEmail {
  return { to, subject, content: `${introduction}\n\n${link}\n\nIf you did not request this, ignore this message. This link can be used only once.` };
}

export function buildAuthEmails(payload: HookPayload, supabaseUrl: string): AuthEmail[] {
  const user = payload?.user;
  const data = payload?.email_data;
  if (!user || !data) throw new Error("Invalid email hook payload");
  const currentEmail = email(user.email);
  const action = value(data.email_action_type);
  if (action === "reauthentication") {
    const code = value(data.token);
    if (!/^\d{6,10}$/.test(code)) throw new Error("Invalid email hook code");
    return [{ to: currentEmail, subject: "Your Union North verification code",
      content: `Your Union North verification code is ${code}. Do not share this code. If you did not request it, ignore this message.` }];
  }
  const target = redirect(data);
  if (action === "email_change") {
    const newEmail = email(user.new_email);
    const newHash = value(data.token_hash);
    const currentHash = value(data.token_hash_new);
    const result: AuthEmail[] = [];
    if (currentHash) {
      result.push(linkedMail(currentEmail, "Confirm your Union North email change",
        "Confirm the change from your current email address:",
        verifyLink(supabaseUrl, currentHash, "email_change", target)));
    }
    result.push(linkedMail(newEmail, "Confirm your new Union North email address",
      "Confirm your new email address:", verifyLink(supabaseUrl, newHash, "email_change", target)));
    return result;
  }
  const messages: Record<string, [string, string, string]> = {
    signup: ["email", "Confirm your Union North account", "Confirm your email address:"],
    invite: ["invite", "Your Union North invitation", "Accept your invitation and set your password:"],
    recovery: ["recovery", "Reset your Union North password", "Reset your password:"],
    magiclink: ["magiclink", "Your Union North sign-in link", "Sign in to your Union North account:"],
  };
  const message = messages[action];
  if (!message) throw new Error("Unsupported email hook action");
  return [linkedMail(currentEmail, message[1], message[2],
    verifyLink(supabaseUrl, value(data.token_hash), message[0], target))];
}
