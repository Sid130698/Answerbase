# Organization invitations and workspace members

An organization owner/admin who is also a workspace admin can create a seven-day invitation for one email and workspace role. The private link is displayed once for manual sharing; no SMTP delivery service is required. Only a SHA-256 token hash is stored. Links carry tokens in the URL fragment, not query parameters or server access logs. Pending links can be revoked. The issuer must still have invitation authority when the link is accepted.

Acceptance requires a signed-in customer identity and a matching confirmed email fetched from Supabase Auth. Editable user metadata is never accepted as proof of verification. Staff identities cannot enroll in client organizations. An existing account in another organization is rejected; an existing account in the same organization can receive the approved workspace membership. The last-workspace-admin invariant still applies.

`VerifiedEmailProvider` isolates identity verification from enrollment policy. A future SSO provider can implement that boundary without changing organization/workspace ownership. No email-domain or subdomain automatically grants organization membership today. Supabase email confirmation must remain enabled (as in local configuration); provider-admin auto-confirmation is a trusted provisioning action.

Workspace admins can add existing organization members, change their uniform admin/editor/viewer roles, or remove membership. Group memberships are removed with workspace access. Email labels are synchronized from authenticated identities; historical users without a recorded email retain a UUID fallback until they sign in.

The separate support console can create/revoke invitations and manage members through an authorized delegated customer identity, with the existing audit trail. Invitation acceptance requires the actual recipient's JWT; there is no delegated enrollment endpoint.

## Local operation

`make local` supplies the public Supabase API key to the server for its user-info request; no service-role key is used for enrollment. Register/confirm the invited account using local Mailpit, then reopen its invitation link. The pending link is retained in that browser tab's session storage through sign-in and removed on acceptance/cancellation. Invite URLs and tokens should not be pasted into public tickets or source control.

Tests cover matching email, atomic enrollment, retry, cross-organization rejection, revoked/expired links, staff exclusion and server-side email confirmation checks. `node scripts/smoke-invitations.mjs` creates a disposable local fixture, provisions its email confirmation in local Supabase only, and tests the real user-info and enrollment flow.

Provider references: [Supabase user fields](https://supabase.com/docs/guides/auth/users), [server-verified getUser](https://supabase.com/docs/reference/javascript/auth-getuser), [email-confirmation configuration](https://supabase.com/docs/guides/auth/general-configuration).

Verified locally: 21 backend tests and frontend lint/build pass; the live local account smoke passes confirmed-email acceptance and viewer membership. Browser checks show accepted invitations and email labels, handle an invalid link without enrollment, and render Access at 390px without page overflow.
