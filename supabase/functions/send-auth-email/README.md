# Microsoft 365 delivery for Supabase Auth

This Edge Function is the Send Email Hook for the shared LineMatrix and Payroll Supabase project. It verifies Supabase's webhook signature, constructs one-time Auth links, obtains a Microsoft Graph app token, and sends from `payroll@unionorth.com`. It does not use the mailbox password or SMTP AUTH.

## Microsoft 365 administrator setup

1. In [Microsoft Entra admin center](https://entra.microsoft.com/), use the **single tenant** app registration for this hook. Its **Application (client) ID** is `c75cb3d2-8b71-4430-85d9-4d4527e38d04` and **Directory (tenant) ID** is `4fd6ab6a-2de9-41d1-8fb5-dba17821ebbc`. No redirect URI is needed for this client credentials app.
2. Under **Certificates & secrets**, create a client secret and copy its **Value** once. Store it directly as the Supabase Edge Function secret `MS_CLIENT_SECRET`; do not put it in Git or chat. Record its expiry and rotate it before then.
3. The Exchange service principal **Object ID** is `6fe8d2ff-ed84-4449-b276-db201c6a11a4`. Exchange PowerShell confirmed it belongs to client ID `c75cb3d2-8b71-4430-85d9-4d4527e38d04` and is named `Union North Payroll SMTP`. Do not run `New-ServicePrincipal` again for this app.
4. The previous local Keycloak setup used SMTP OAuth (`SMTP.SendAsApp`) and mailbox `FullAccess`/`SendAs` grants. Those do **not** establish the `Application Mail.Send` RBAC grant used by this Graph `sendMail` hook. On 2026-09-26, the `UnionNorthPayrollAuthMailSend` assignment was created against `UnionNorthPayrollAuthSender`. `Test-ServicePrincipalAuthorization` returned `InScope=True` for `payroll@unionorth.com` and `InScope=False` for `ie@unionorth.com`. Do not create the scope or role assignment again.
5. **Verified 2026-09-26:** a fresh Microsoft Graph token for this client now has an empty `roles` array. Its earlier tenant-wide Graph `Mail.Send` grant has been revoked. Confirm Microsoft Graph application `Mail.Send` is also removed from **App registrations → this app → API permissions** so it is not requested again. Keep the existing Exchange Online `SMTP.SendAsApp` permission if the local Keycloak SMTP flow still needs it. Entra grants and Exchange RBAC grants are additive; do not grant unscoped Graph `Mail.Send` again.

```powershell
Install-Module ExchangeOnlineManagement -Scope CurrentUser
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope Process -Force
Import-Module ExchangeOnlineManagement
Connect-ExchangeOnline

$appId = 'c75cb3d2-8b71-4430-85d9-4d4527e38d04'
$servicePrincipalId = '6fe8d2ff-ed84-4449-b276-db201c6a11a4'
$sender = 'payroll' + [char]64 + 'unionorth.com'
Get-ServicePrincipal -Identity $servicePrincipalId | Format-List DisplayName,AppId,ObjectId
Test-ServicePrincipalAuthorization -Identity $servicePrincipalId | Format-Table RoleName,AllowedResourceScope,InScope
Test-ServicePrincipalAuthorization -Identity $servicePrincipalId -Resource $sender | Format-Table RoleName,AllowedResourceScope,InScope
# Select a different real tenant mailbox for the negative check.
$other = Get-EXOMailbox -ResultSize 100 | Where-Object { $_.PrimarySmtpAddress -ne $sender } | Select-Object -First 1 -ExpandProperty PrimarySmtpAddress
if (-not $other) { throw 'A second mailbox is required for the negative check.' }
$other
Test-ServicePrincipalAuthorization -Identity $servicePrincipalId -Resource $other | Format-Table RoleName,AllowedResourceScope,InScope
```

The Exchange service principal, management scope, and role assignment already exist. Exchange organization customization was enabled on 2026-09-26 (`IsDehydrated=False`). The following commands document the one-time setup and are not intended to be rerun in this tenant.

```powershell
Get-OrganizationConfig | Format-List IsDehydrated
if ((Get-OrganizationConfig).IsDehydrated) { Enable-OrganizationCustomization }
Get-OrganizationConfig | Format-List IsDehydrated
```

The conditional runs `Enable-OrganizationCustomization` only when the status is `True`; Microsoft says running it again after customization is enabled returns an error. Create the mailbox scope and scoped role assignment below only after verifying the recipient filter matches just the intended mailbox. If the scope or assignment already exists, inspect it rather than creating duplicates.

```powershell
$ErrorActionPreference = 'Stop'
$servicePrincipalId = '6fe8d2ff-ed84-4449-b276-db201c6a11a4'
$sender = 'payroll' + [char]64 + 'unionorth.com'
$filter = "EmailAddresses -eq '$sender'"
$scopeRecipients = @(Get-Recipient -Filter $filter)
$scopeRecipients | Format-Table Name,PrimarySmtpAddress
if ($scopeRecipients.Count -ne 1 -or [string]$scopeRecipients[0].PrimarySmtpAddress -ine $sender) { throw 'The scope filter does not match exactly the payroll mailbox.' }
New-ManagementScope -Name 'UnionNorthPayrollAuthSender' -RecipientRestrictionFilter $filter
New-ManagementRoleAssignment -Name 'UnionNorthPayrollAuthMailSend' -App $servicePrincipalId -Role 'Application Mail.Send' -CustomResourceScope 'UnionNorthPayrollAuthSender'
Test-ServicePrincipalAuthorization -Identity $servicePrincipalId -Resource $sender | Format-Table RoleName,AllowedResourceScope,InScope
$other = Get-EXOMailbox -ResultSize 100 | Where-Object { $_.PrimarySmtpAddress -ne $sender } | Select-Object -First 1 -ExpandProperty PrimarySmtpAddress
if (-not $other) { throw 'A second mailbox is required for the negative check.' }
$other
Test-ServicePrincipalAuthorization -Identity $servicePrincipalId -Resource $other | Format-Table RoleName,AllowedResourceScope,InScope
```

Exchange RBAC changes can take 30 minutes to 2 hours to reach Graph even when the PowerShell test already shows the intended scope. Keep Microsoft Security Defaults enabled.

## Supabase activation

Target project: `qhayxwdrjthvuoshodgy`. The following Edge Function secrets are required:

| Secret | Value source |
| --- | --- |
| `MS_TENANT_ID` | `4fd6ab6a-2de9-41d1-8fb5-dba17821ebbc` (already set) |
| `MS_CLIENT_ID` | `c75cb3d2-8b71-4430-85d9-4d4527e38d04` (already set) |
| `MS_CLIENT_SECRET` | Entra client secret **Value** (set on 2026-09-26) |
| `SEND_EMAIL_HOOK_SECRET` | Signing secret shared with Supabase Auth's Send Email Hook, including `v1,whsec_` prefix (set on 2026-09-26) |

`SUPABASE_URL` is provided automatically by the Edge Function runtime. Add the four secrets in Supabase **Edge Functions → Secrets**, or use `supabase secrets set` from a private local file. Deploy with:

```sh
supabase functions deploy send-auth-email --project-ref qhayxwdrjthvuoshodgy --no-verify-jwt
```

The HTTP Send Email Hook is enabled in Supabase **Authentication → Auth Hooks → Send Email** with this URI:

```text
https://qhayxwdrjthvuoshodgy.supabase.co/functions/v1/send-auth-email
```

The same signing secret is configured in Supabase Auth and the Edge Function. Keep the **Email** provider enabled. The hook replaces SMTP for Auth emails while enabled. Disable the failed custom SMTP configuration after successful end-to-end testing.

Test one recovery email to `dev@tradexsolution.com` and an invitation to a separate approved inbox. Confirm a Graph HTTP 202, a message in `payroll@unionorth.com` Sent Items, actual delivery, and successful link use. Graph 202 means accepted, not delivered. Check Microsoft 365 message trace if the message is accepted but absent from the recipient inbox. Avoid repeated requests while diagnosing failures.

As of 2026-09-26, a direct Microsoft Graph send test to `dev@tradexsolution.com` returned HTTP 202. The signed Auth Hook was enabled, and an API recovery request without PKCE returned HTTP 200. A Payroll browser recovery request then failed because its PKCE token hash has a `pkce_` prefix that the hook's token validator rejected. The validator now preserves that prefix; the function was redeployed as version 6, and one recovery request with a PKCE challenge returned HTTP 200 in about 2.4 seconds. The function logs showed no further token-validation error after that request. The recipient must still confirm delivery and successful reset-link use. The separate invitation test remains unverified.

References: [Supabase Send Email Hook](https://supabase.com/docs/guides/auth/auth-hooks/send-email-hook), [Supabase Auth Hook timing](https://supabase.com/docs/guides/auth/auth-hooks), [Microsoft Graph sendMail](https://learn.microsoft.com/en-us/graph/api/user-sendmail?view=graph-rest-1.0), [Exchange RBAC for Applications](https://learn.microsoft.com/en-us/exchange/permissions-exo/application-rbac).
