# Registration and login

Answerbase uses Supabase Auth for email/password registration, confirmation emails, password recovery, refresh tokens, and sign-out. Passwords go directly from the browser to Supabase; the Java API never receives or stores them.

The customer application is gated by a session and a successful `GET /api/v1/me`. Spring Security validates the access token signature using the project's JWKS, issuer, expiration, authenticated audience/role, and UUID subject. There is no development bypass. Missing configuration fails closed. This endpoint returns identity and the account’s organization (or null before enrollment). Organization assignment and workspace memberships are application-owned; see [organization access](organizations.md). Only explicitly registered application routes are allowed.

## Local setup (no hosted account or email provider required)

Docker must be running. From the repository root:

```sh
node scripts/init-local-auth.mjs
npx --yes supabase@3.3.0 start -x studio,realtime,storage-api,imgproxy,edge-runtime,logflare,vector,supavisor
node scripts/configure-local-auth.mjs
docker compose up --build -d --wait
```

The helper generates a private ES256 key in the ignored `supabase/signing_keys.json`. Do not share or commit it. The second helper copies only the public browser configuration into ignored env files and sets the API issuer/JWKS configuration. The Supabase CLI publishes development ports on the host; use this test stack only on a trusted development machine. Supabase uses separate ports/data from the existing application database; Flyway remains the application schema owner.

- App: http://localhost:5173
- Local email inbox (Mailpit): http://127.0.0.1:55324
- Supabase gateway: http://127.0.0.1:55321
- Developer Swagger: http://localhost:5173/swagger-ui/index.html

Register with a test email and a password of at least 12 characters. Open the confirmation email in Mailpit and follow its link in the same browser. PKCE binds email link completion to the browser that requested it. Verify sign-in, reload/session restoration, sign-out, invalid-password feedback, and password recovery. Mailpit captures emails locally; it does not deliver to real inboxes.

For host-run Vite, `frontend/.env.local` is populated by the helper. For host-run Java, use `SUPABASE_AUTH_ISSUER=http://127.0.0.1:55321/auth/v1` and leave `SUPABASE_JWKS_URL` empty (the Compose-specific hostname is only for containers).

Stop local Auth with `npx --yes supabase@3.3.0 stop`. This preserves local data. The app still uses `docker compose down` separately.

## Verification

`./backend/mvnw -f backend/pom.xml verify` checks actual JWT signatures and issuer/audience/role/subject/expiry validation using a local JWKS test server. `npm --prefix frontend run lint` and `npm --prefix frontend run build` check the frontend.

With the local stack running, `node frontend/scripts/auth-smoke.mjs` tests signup, confirmation, rejected unconfirmed/wrong-password login, successful login, protected identity, refresh, logout, and password recovery. It creates synthetic users and emails in the local test database/inbox. It refuses to run against a hosted Supabase URL. Pass an account count (1–5), for example `node frontend/scripts/auth-smoke.mjs 3`, to also verify multiple distinct accounts and repeated account switching with refresh-token revocation after logout.

## Hosted configuration

Use a Supabase project with asymmetric ES256 or RS256 signing keys. In Authentication settings, enable email/password signup and email confirmation, set minimum password length to 12, and configure the exact deployed app origin as Site URL and redirect URL. For local frontend testing include `http://localhost:5173/`. Configure an email sender for real users; the hosted default email sender has restrictions and is not a public signup delivery solution.

Set these in the root `.env` for Compose, or the relevant frontend build and API deployment environments:

```dotenv
VITE_SUPABASE_URL=https://<project-ref>.supabase.co
VITE_SUPABASE_PUBLISHABLE_KEY=<public-publishable-key>
SUPABASE_AUTH_ISSUER=https://<project-ref>.supabase.co/auth/v1
SUPABASE_JWKS_URL=
```

Vite values are public and embedded at build time; rebuild after changing them. Never place service-role, secret API, or private signing keys in VITE variables. The production frontend must proxy `/api` to the backend (or explicitly configure and constrain CORS for a separate API origin).

The Supabase SDK manages persisted sessions and refresh. Sign-out removes this browser's session and revokes its refresh token; already-issued access tokens can remain valid until expiry. Future sensitive operations may require fresh authentication. Use HTTPS for deployed services. Authorization must use app-owned membership, never user-editable metadata.

## API documentation

Registration/login/reset are Supabase-owned endpoints, not duplicate Spring controllers. Their JavaScript calls are `signUp`, `signInWithPassword`, `resetPasswordForEmail`, `updateUser`, and `signOut`. Spring’s Swagger documents identity, organization creation, workspaces, and workspace memberships with bearer authorization alongside public health probes. Paste a test user's access token into Swagger's Authorize field to exercise the protected endpoint; do not store tokens in docs.

References: [Supabase password auth](https://supabase.com/docs/guides/auth/passwords), [JWT verification](https://supabase.com/docs/guides/auth/jwts), [local auth testing](https://supabase.com/docs/guides/auth/testing-auth-flows).
