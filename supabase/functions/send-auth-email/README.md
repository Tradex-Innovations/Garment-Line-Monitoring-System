# Microsoft 365 delivery for Supabase Auth

This Edge Function is the Send Email Hook for the shared LineMatrix and Payroll Supabase project. It verifies Supabase's webhook signature, constructs one-time Auth links, obtains a Microsoft Graph app token, and sends from `payroll@unionorth.com`. It does not use the mailbox password or SMTP AUTH.

## Microsoft 365 administrator setup

1. In [Microsoft Entra admin center](https://entra.microsoft.com/), use the **single tenant** app registration for this hook. Its **Application (client) ID** is `c75cb3d2-8b71-4430-85d9-4d4527e38d04` and **Directory (tenant) ID** is `4fd6ab6a-2de9-41d1-8fb5-dba17821ebbc`. No redirect URI is needed for this client credentials app.
2. Under **Certificates & secrets**, create a client secret and copy its **Value** once. Store it directly as the Supabase Edge Function secret `MS_CLIENT_SECRET`; do not put it in Git or chat. Record its expiry and rotate it before then.
3. In **Enterprise applications**, find the new app and record the **Object ID of its service principal**. This is different from the Object ID shown on App registrations.
4. Have an Exchange administrator run the PowerShell below. Replace the placeholder GUID with the **Enterprise application service principal Object ID**. The scope should match exactly one recipient. Confirm `InScope=True` for `payroll@unionorth.com` and `InScope=False` for another real mailbox.
5. In Entra **API permissions**, ensure this app has **no tenant-wide Microsoft Graph `Mail.Send` application grant**. The Exchange role assignment below grants scoped `Application Mail.Send`. Entra grants and Exchange RBAC grants are additive, so an unscoped Entra grant would defeat the mailbox scope.

```powershell
Install-Module ExchangeOnlineManagement -Scope CurrentUser
Connect-ExchangeOnline

$appId = 'c75cb3d2-8b71-4430-85d9-4d4527e38d04'
$servicePrincipalId = '<ENTERPRISE_APPLICATION_OBJECT_ID>'
New-ServicePrincipal -AppId $appId -ObjectId $servicePrincipalId -DisplayName 'Union North Supabase Auth Mail'
New-ManagementScope -Name 'UnionNorthPayrollAuthSender' -RecipientRestrictionFilter "EmailAddresses -eq 'payroll@unionorth.com'"
Get-Recipient -Filter "EmailAddresses -eq 'payroll@unionorth.com'" | Format-Table Name,PrimarySmtpAddress
New-ManagementRoleAssignment -Name 'UnionNorthPayrollAuthMailSend' -App $servicePrincipalId -Role 'Application Mail.Send' -CustomResourceScope 'UnionNorthPayrollAuthSender'
Test-ServicePrincipalAuthorization -Identity $servicePrincipalId -Resource payroll@unionorth.com | Format-Table RoleName,InScope
Test-ServicePrincipalAuthorization -Identity $servicePrincipalId -Resource '<ANOTHER_REAL_MAILBOX>' | Format-Table RoleName,InScope
```

Exchange RBAC changes can take 30 minutes to 2 hours to reach Graph even when the PowerShell test already shows the intended scope. Keep Microsoft Security Defaults enabled.

## Supabase activation

Target project: `qhayxwdrjthvuoshodgy`. The following Edge Function secrets are required:

| Secret | Value source |
| --- | --- |
| `MS_TENANT_ID` | `4fd6ab6a-2de9-41d1-8fb5-dba17821ebbc` (already set) |
| `MS_CLIENT_ID` | `c75cb3d2-8b71-4430-85d9-4d4527e38d04` (already set) |
| `MS_CLIENT_SECRET` | Entra client secret **Value** |
| `SEND_EMAIL_HOOK_SECRET` | Supabase Auth Hooks generated signing secret, including `v1,whsec_` prefix |

`SUPABASE_URL` is provided automatically by the Edge Function runtime. Add the four secrets in Supabase **Edge Functions → Secrets**, or use `supabase secrets set` from a private local file. Deploy with:

```sh
supabase functions deploy send-auth-email --project-ref qhayxwdrjthvuoshodgy --no-verify-jwt
```

Only after Graph sending from the scoped mailbox succeeds, go to Supabase **Authentication → Auth Hooks → Send Email**, select **HTTP**, enter:

```text
https://qhayxwdrjthvuoshodgy.supabase.co/functions/v1/send-auth-email
```

Generate the hook signing secret there and store it as `SEND_EMAIL_HOOK_SECRET` before enabling the hook. Keep the **Email** provider enabled. The hook replaces SMTP for Auth emails when enabled. Disable the failed custom SMTP configuration after successful end-to-end testing.

Test one recovery email to `dev@tradexsolution.com` and an invitation to a separate approved inbox. Confirm a Graph HTTP 202, a message in `payroll@unionorth.com` Sent Items, actual delivery, and successful link use. Graph 202 means accepted, not delivered. Check Microsoft 365 message trace if the message is accepted but absent from the recipient inbox. Avoid repeated requests while diagnosing failures.

References: [Supabase Send Email Hook](https://supabase.com/docs/guides/auth/auth-hooks/send-email-hook), [Supabase Auth Hook timing](https://supabase.com/docs/guides/auth/auth-hooks), [Microsoft Graph sendMail](https://learn.microsoft.com/en-us/graph/api/user-sendmail?view=graph-rest-1.0), [Exchange RBAC for Applications](https://learn.microsoft.com/en-us/exchange/permissions-exo/application-rbac).
