# Answerbase staff support

[Saved visual design](../answerbase-support-design.html).

## Independent applications

- Customer app: `frontend/`, local http://localhost:5173.
- Staff app: `support-frontend/`, local http://localhost:5174.
- Each has its own package/lockfile, Vite config, build, Docker image and CI job. The customer app does not import the staff console.
- The staff app embeds the real customer app in a cross-origin sandboxed iframe. The customer app contains a small delegated-session adapter; it does not duplicate the customer product UI.
- The API deployment is shared today. Staff routes (`/api/v1/support`), customer routes and delegated routes (`/api/v1/delegated`) have separate authorization paths. Customer nginx rejects staff API routes; staff nginx proxies only staff routes. Direct API requests still require backend authorization.

## Staff identities and login

Only explicitly provisioned Answerbase employee accounts can access staff APIs. Staff accounts are stored in `staff_access`, not in customer organization membership. PostgreSQL triggers prohibit one identity from appearing in both `staff_access` and `users`, even for inactive staff. A dedicated staff login uses its own browser session-storage key. The console has no signup or domain-suffix allowlist.

`support` and `platform_admin` can both diagnose and repair client workspace access and open a delegated user view. Platform admins additionally see all audit records; support sees its own. Staff management is a trusted developer operation for this local version, not a customer-facing or self-service API.

Provision a dedicated confirmed local Supabase account that has **not** created/joined a customer organization. Then run:

```sh
node scripts/provision-local-staff.mjs STAFF_USER_UUID support
# Or platform_admin. Revoke immediately with:
node scripts/provision-local-staff.mjs STAFF_USER_UUID revoke
```

The command validates the account against the local Supabase Docker database and logs the local provisioning operation. Its audit detail explicitly identifies the operator as a local developer CLI rather than pretending the target account performed the grant. There is no automatic staff assignment for client owners or an Answerbase-looking email address.

## User context versus staff repairs

Start a company-scoped support session with a ticket reference/reason (30-minute maximum). Choose an existing company user to issue a random 256-bit opaque grant (10-minute maximum, capped by the session). The database stores only its SHA-256 hash. Each new grant revokes previous grants in that support session.

The parent sends the grant through `postMessage` with an exact target origin. Both applications verify exact origin and window source. The iframe never receives a staff JWT. It holds the grant only in memory and does not initialize a normal customer Supabase session. Tokens never appear in iframe URLs, persistent customer storage, or audit details.

Delegated endpoints revalidate grant validity, session expiry/closure, active staff access, and the target user's organization on every request. They call the same customer workspace domain services. A member cannot create a workspace inside the frame merely because staff can repair access outside it. The delegated allowlist now covers workspace, document, retrieval, answer, member, invitation and settings operations; each route explicitly validates the grant and records its operation. Future features must extend this surface with corresponding authorization and audit tests.

Staff repair endpoints separately enforce the support-session organization and existing-user membership. They preserve the same cross-company foreign keys and last-workspace-admin invariant as customer APIs. A customer organization role is never changed by selecting a user or issuing a grant.

The console displays a persistent staff/company/customer banner and an explicit end-session button. Ending the session or revoking staff access invalidates future delegated requests immediately. Closing a tab without ending the session does not reliably notify a server; remaining grants expire server-side. Reloading the console can restore a session ID from the URL, but requires a new user grant. No credentials are persisted for the embedded context.

## Audit

Successful support reads, grant issuance, membership repairs, delegated customer operations and session closure are recorded with real staff actor, company, session and affected user. Changes and audit entries commit in the same transaction. Membership repairs record before/after roles. Support audit records reject UPDATE/DELETE through a database trigger. This is an application audit trail, not a separate tamper-proof archive: a database owner can change triggers. Denied/failed attempts are not yet persisted in this audit table.

## Deployment

```sh
node scripts/configure-local-auth.mjs
npm --prefix support-frontend ci
npm --prefix support-frontend run dev
# Or both independent frontend containers and the API:
docker compose up --build -d --wait
```

For separate servers, set customer build `VITE_SUPPORT_ORIGIN` and customer nginx `SUPPORT_ORIGIN` to the exact HTTPS staff origin. Set staff build `VITE_CUSTOMER_ORIGIN` to the exact HTTPS customer origin. Keep them different origins. Point nginx `CUSTOMER_API_UPSTREAM` / `SUPPORT_API_UPSTREAM` at the API reachable by each server. Restrict upstream ingress as appropriate to the deployment. Customer CSP allows framing only from its configured staff origin; staff CSP prohibits framing. Vite development CSP is configured for the local ports; change it explicitly for a different development origin.

SSO, company-domain enrollment and tenant-hostname routing are future authentication/enrollment adapters, not authorization signals. Staff SSO must still resolve a provisioned active staff account. Customer SSO must resolve a customer account. Production SSO and staff MFA enrollment remain future work. Customer invitations, document ingestion and customer password recovery are implemented separately.

## Verification

Backend integration tests cover customer denial from staff APIs, staff/customer exclusivity, organization scope, session ownership, true customer permissions, support repair, audit actor attribution, grant switching, expiry, closure and staff revocation. Run `./backend/mvnw -f backend/pom.xml verify` with local Postgres available.

Run `node frontend/scripts/support-smoke.mjs` against the running local stack for real Supabase and both nginx proxies. Synthetic staff/customer accounts and an organization remain for inspection. Local demo credentials are saved to the ignored `.cache/support-demo.json`, never committed. UI lint/build is independent in both frontend packages. Browser visual/frame-message validation requires a working browser automation connection and is not replaced by the API smoke test.
