# Organizations and access

Answerbase uses one shared application Postgres database. A user account belongs to exactly one organization. Users may have different roles in any subset of that organization's workspaces. No membership row means no workspace access, including for organization owners and admins.

## Separate responsibilities

1. Authentication verifies a token and supplies a stable user UUID. Supabase is the current identity provider; passwords and SSO protocol details stay outside domain services.
2. Organization enrollment associates that UUID with one organization in `answerbase.users`. Self-service creation and explicit administrator approval are separate enrollment paths.
3. Workspace authorization resolves membership from the database on each request. It never trusts an organization ID from the browser, email suffix, token user metadata, Host header, or URL.

`OrganizationService`, `WorkspaceService`, and `WorkspaceMembershipService` receive UUIDs rather than JWTs or Supabase SDK objects. HTTP controllers adapt the verified principal. The current application user ID equals the Supabase subject UUID. If replacing Supabase with an identity provider using different subjects, add an issuer/subject-to-user mapping at that boundary; never map users solely by matching email strings.

Future SSO/domain/subdomain onboarding can supply another trusted enrollment path without changing workspace membership. Verified domains and SSO connections should be stored separately from organization display names. A future `company.answerbase.com` hostname may route users to a login experience, but is not proof of organization membership. DNS, domain verification, SSO, tenant routing, and automatic domain enrollment are not implemented now.

## Data constraints

- `organizations(id, name)` stores company identity. Names are display labels, are not unique, and do not claim a domain.
- `users(id, organization_id, organization_role)` has one globally unique user ID and a required organization ID.
- `workspaces(id, organization_id, created_by, ...)` references its creator within the same organization.
- `workspace_members(organization_id, workspace_id, user_id, role)` has composite foreign keys to both the organization/user pair and organization/workspace pair. PostgreSQL rejects cross-company memberships even if application code makes a mistake.

V3 preserves legacy development workspaces in one isolated organization per creator. It refuses to automatically migrate legacy shared workspaces: those require an explicit company mapping. It does not infer organizations from email addresses. Existing legacy `member` workspace roles become `editor`.

## Roles

Role meanings are owned by Answerbase and apply uniformly to all companies. Organization owner/admin can create workspaces; member cannot. Creating a workspace assigns its creator workspace admin. Company roles never implicitly grant workspace membership.

All workspace roles can read their own workspace. Only workspace admin can list/manage memberships. The supported workspace roles are `admin`, `editor`, and `viewer`. Editor versus viewer document-write differences are reserved for document endpoints, which do not exist yet; no document permission is claimed to be implemented. Membership changes serialize on the workspace row, recheck caller access, and cannot remove or demote the last workspace admin.

## Onboarding and API

A confirmed account without an organization can sign in and create an organization, becoming owner atomically. Concurrent retries with the same name return the existing organization. A second organization is rejected. Accounts awaiting approval should use “Check my access” instead of creating another organization.

- `GET /api/v1/me` returns identity plus `organization: {id,name,role}`, or null before enrollment.
- `POST /api/v1/organizations` accepts `{ "name": "Acme" }`, returns 200, and requires authentication. No endpoint lists or switches organizations.
- Workspace create/list/read remain under `/api/v1/workspaces`. Responses include `organizationId`. Create is organization owner/admin only; list/read require membership.
- `GET /api/v1/workspaces/{id}/members`: workspace admin only.
- `PUT /api/v1/workspaces/{id}/members/{userId}`: assign `{ "role": "viewer" }` (or editor/admin) to an existing user in the same organization; 204 on success.
- `DELETE /api/v1/workspaces/{id}/members/{userId}`: revoke membership, 204 on success.

Missing membership and other-company workspaces return 404. Enrolled users without the required role receive 403; last-admin removal returns 409. Membership management is currently API-only, available in Swagger; there is no customer-facing membership editor yet.

## Approving an existing-company join locally

The production approval/invitation UI is not implemented. A trusted local developer can approve a confirmed local Supabase user explicitly:

```sh
node scripts/approve-local-organization-member.mjs ORGANIZATION_UUID SUPABASE_USER_UUID member
```

Get the organization ID from the owner's `/api/v1/me` response and the user ID from the joining user's response. The script only connects to the named local Docker services, verifies that the Supabase account has a confirmed email, and refuses to move an account already enrolled elsewhere. It adds no workspace access. A workspace admin then uses the membership API to assign the desired workspaces/roles. Re-running approval leaves existing roles unchanged.

Do not use an unverified email domain as approval. Existing memberships remain independent of email changes. Future domain/SSO lifecycle policy must define suspension and re-enrollment explicitly.
