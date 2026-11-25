# Workspaces

Enrolled organization owners/admins can create workspaces. All users can open and switch only workspaces they belong to. See [Organizations](organizations.md) for enrollment and role rules. The first login without an organization shows organization setup. Organization members without workspace access see an access-pending screen. Selection is stored in the `workspace` URL query parameter; browser back/forward works. Each workspace currently has empty Ask and Sources views. Uploads and invitations are not implemented.

## Data and access

Flyway V2 creates `answerbase.workspaces` and `answerbase.workspace_members` in the application Postgres database. Supabase supplies the verified user UUID; credentials remain in Supabase Auth. No separate database, server folder, or additional service is created for a workspace.

Creating a workspace inserts the workspace and its creator's `admin` membership in one transaction. All reads filter by the authenticated user's membership. Unknown and inaccessible workspace IDs both return 404. Future document endpoints must apply the same membership check; merely having a valid login is insufficient.

## API

Swagger at `http://localhost:5173/swagger-ui/index.html` documents:

- `POST /api/v1/workspaces`: `{ "name": "Customer support" }`, with a UUID `Idempotency-Key` header. Returns 201 plus a Location header; repeating the same key and normalized name returns the same workspace with 200. Changing the name under the same key returns 409.
- `GET /api/v1/workspaces`: memberships for the caller only.
- `GET /api/v1/workspaces/{id}`: one accessible workspace.

All require a Supabase bearer access token. Names are trimmed, 1–80 characters, without control characters. A preview limit of 10 created workspaces per user is enforced. PostgreSQL transaction-scoped advisory locking serializes creation for each user to protect retries and the quota. Request keys are stored for the lifetime of the workspace. No workspace deletion, renaming, or invitations are exposed yet. Workspace admins can manage explicit memberships through the membership API.

## Verification

`./backend/mvnw -f backend/pom.xml verify` runs real PostgreSQL and signed JWT integration tests, including validation, creator membership, access isolation, replay conflicts and concurrent retries. Start the local databases first.

`node frontend/scripts/auth-smoke.mjs 3` exercises three synthetic accounts against local Supabase and the frontend proxy, including workspace creation, retry, listing, isolation and session switching. Synthetic accounts and their workspaces remain locally for inspection.
