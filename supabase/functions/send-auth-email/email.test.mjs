import assert from "node:assert/strict";
import test from "node:test";
import { buildAuthEmails } from "./email.ts";

const hash = "a".repeat(64);
const otherHash = "b".repeat(64);
const base = {
  user: { email: "dev@tradexsolution.com" },
  email_data: {
    email_action_type: "recovery", token_hash: hash,
    redirect_to: "https://union-north-payroll.netlify.app/auth/set-password",
  },
};

test("recovery link verifies at Supabase then returns to the Payroll password page", () => {
  const [mail] = buildAuthEmails(base, "https://qhayxwdrjthvuoshodgy.supabase.co");
  assert.equal(mail.to, base.user.email);
  const link = mail.content.match(/https:\/\/\S+/)?.[0];
  const url = new URL(link);
  assert.equal(url.pathname, "/auth/v1/verify");
  assert.equal(url.searchParams.get("type"), "recovery");
  assert.equal(url.searchParams.get("token"), hash);
  assert.equal(url.searchParams.get("redirect_to"), base.email_data.redirect_to);
});

test("recovery link preserves the PKCE token prefix used by the browser", () => {
  const prefixedHash = `pkce_${"a".repeat(56)}`;
  const [mail] = buildAuthEmails({ ...base, email_data: { ...base.email_data,
    token_hash: prefixedHash } }, "https://qhayxwdrjthvuoshodgy.supabase.co");
  const link = mail.content.match(/https:\/\/\S+/)?.[0];
  assert.equal(new URL(link).searchParams.get("token"), prefixedHash);
});

test("invite links use the invite verification type", () => {
  const [mail] = buildAuthEmails({ ...base, email_data: { ...base.email_data,
    email_action_type: "invite" } }, "https://qhayxwdrjthvuoshodgy.supabase.co");
  assert.match(mail.content, /type=invite/);
});

test("secure email change sends each token hash to its matching mailbox", () => {
  const [current, next] = buildAuthEmails({
    user: { email: "old@example.com", new_email: "new@example.com" },
    email_data: { ...base.email_data, email_action_type: "email_change",
      token_hash: hash, token_hash_new: otherHash },
  }, "https://qhayxwdrjthvuoshodgy.supabase.co");
  assert.equal(current.to, "old@example.com");
  assert.match(current.content, new RegExp(otherHash));
  assert.equal(next.to, "new@example.com");
  assert.match(next.content, new RegExp(hash));
});

test("single confirmation email change goes only to new address", () => {
  const [mail] = buildAuthEmails({
    user: { email: "old@example.com", new_email: "new@example.com" },
    email_data: { ...base.email_data, email_action_type: "email_change" },
  }, "https://qhayxwdrjthvuoshodgy.supabase.co");
  assert.equal(mail.to, "new@example.com");
});

test("rejects unsupported actions and unsafe destinations", () => {
  assert.throws(() => buildAuthEmails({ ...base, email_data: { ...base.email_data,
    email_action_type: "other" } }, "https://qhayxwdrjthvuoshodgy.supabase.co"));
  assert.throws(() => buildAuthEmails({ ...base, email_data: { ...base.email_data,
    redirect_to: "javascript:alert(1)" } }, "https://qhayxwdrjthvuoshodgy.supabase.co"));
  assert.throws(() => buildAuthEmails({ ...base, email_data: { ...base.email_data,
    token_hash: `pkce_${"z".repeat(56)}` } }, "https://qhayxwdrjthvuoshodgy.supabase.co"));
});

test("reauthentication sends a code rather than a link", () => {
  const [mail] = buildAuthEmails({ ...base, email_data: { email_action_type: "reauthentication",
    token: "123456" } }, "https://qhayxwdrjthvuoshodgy.supabase.co");
  assert.match(mail.content, /123456/);
  assert.doesNotMatch(mail.content, /https:/);
});
