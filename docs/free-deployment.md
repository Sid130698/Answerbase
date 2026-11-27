# Free deployment

The deployment target is a small authenticated pilot, with limits enforced in the application. Free tiers are not a production SLA or a guarantee that free capacity will be available. Do not upgrade accounts or enable paid overages for this setup.

## Providers and boundaries

| Provider | What runs there | Free-tier constraint checked 2026-11-28 |
| --- | --- | --- |
| Oracle Cloud Always Free | Ubuntu ARM VM: Java API, isolated worker, PostgreSQL/pgvector, local embeddings/reranker, two independent frontend containers, Caddy, monitor | Current A1 allowance is **2 OCPUs / 12 GB**, not the old 4/24 allowance. Capacity depends on home region; idle instances can be reclaimed. Terraform requests one 2/12 VM and 50 GB boot disk. |
| Supabase Free | Auth and a **private** bucket containing original documents | 1 GB storage, 500 MB managed database; the application database stays on the VM because 50,000 embeddings exceed a practical 500 MB database budget. Projects can pause after inactivity. |
| Groq free developer access | Answer generation only | Model-specific request/token limits; queued retries handle transient outages. Embeddings and reranking stay on the VM. |
| Brevo Free (optional SMTP choice) | Supabase confirmation/reset emails | 300 emails/day. Sender/domain verification is required. Supabase's default email service is not sufficient for arbitrary public signups. |
| GitHub Actions | Checks and live model evaluation on disposable runners | Standard hosted runners are free for public repositories; private-repository allowances differ. No paid model keys are required by the local-model evaluation. |

Sources: [Oracle](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm), [Supabase](https://supabase.com/pricing), [Groq](https://console.groq.com/docs/rate-limits), [Supabase SMTP](https://supabase.com/docs/guides/auth/auth-smtp), [Brevo](https://help.brevo.com/hc/en-us/articles/208589409-About-Brevo-s-pricing-plans), [GitHub](https://docs.github.com/en/actions/concepts/billing-and-usage).

Both React applications are independently built/deployed services. The default topology serves them on separate HTTPS hostnames through Caddy on the VM. Cloudflare Pages is an optional later static-hosting choice; it is not needed by this deployment and cannot run the Java API. Use two DNS names you control (including suitable free DNS subdomains); a paid domain is not technically required. DNS ownership and public certificate issuance must be verified on the real deployment.

## Provision once

1. Create the Oracle, Supabase and Groq accounts. Account identity checks, account agreements, and any requested payment verification belong to the account owner. Select only resources actually labeled Always Free in your account's **home region**.
2. Configure OCI CLI/API credentials locally using Oracle's instructions. Copy `infra/terraform/example.tfvars.example` to an ignored `terraform.tfvars` in that directory. Supply your compartment, availability domain, Ubuntu 24.04 ARM image OCID, SSH **public** key, and your current administrator IPv4 `/32`. Never put private keys in Terraform variables or git.
3. Run `terraform init`, `terraform plan -out=release.tfplan`, inspect the plan and account capacity, then `terraform apply release.tfplan`. The validated configuration creates a VM, VCN/subnet, route and ingress rules for 80/443 plus SSH from your administrator IP. No database, API, model or worker port is public. Actual OCI planning/apply requires account credentials and has not been executed locally.
4. Wait for `cloud-init status --wait` on the VM. Bootstrap installs Docker/Compose from Docker's repository and a checksum-verified Node 24 ARM binary from nodejs.org. Clone this repository into `/opt/answerbase` as `ubuntu`, and check out the reviewed release commit. Docker access is privileged; keep the host account restricted to trusted operators.
5. Point the customer and staff DNS hostnames to the VM's public IP. Caddy obtains certificates using HTTP/HTTPS validation. Keep both ports reachable. Do not bypass certificate errors.

## Configure hosted identity and originals

1. Create a Supabase project and enable an asymmetric JWT signing key. Copy its URL, publishable key, issuer and JWKS URL into a private configuration file. The Java API trusts only that project's issuer/JWKS.
2. Create bucket `answerbase-documents` with **Public disabled**. Do not add client read/write policies; the API and worker use a server credential and enforce tenant/group permissions before accessing originals. Store the server credential only on the host.
3. In Supabase Auth, configure the customer site URL and explicit redirects for the customer origin followed by `/` (the current PKCE confirmation/reset callback). The separate staff frontend signs in provisioned accounts; include its exact origin only if enabling a staff redirect flow. See `docs/authentication.md`; do not allow broad wildcard redirects. Configure custom SMTP and verify the sender. Test confirmation, password reset and logout on both origins.
4. Create a Groq API key and select an available model. The deployment preflight verifies that the selected model exists for the configured key. Set a project spending limit and retain free access; the app does not manage provider billing.
5. Copy `infra/deploy/.env.example` to `/opt/answerbase/.env.production`; run `chmod 600 .env.production`. Replace every placeholder. Use matching exact HTTPS origins and distinct customer/staff hosts. Never paste keys into chat, Swagger, frontend configuration, PRs, or committed files. Only the Supabase publishable key belongs in the frontend.

Run from `/opt/answerbase`:

```sh
node scripts/deploy.mjs --check
node scripts/deploy.mjs
```

`--check` validates local values and Compose without contacting providers or creating resources. Deployment additionally checks the private bucket, asymmetric JWKS and Groq model access, pulls the embedding model, builds/starts services and waits for container health. Verify public HTTPS and one end-to-end upload/cited answer before inviting testers. The hosted checks cannot be exercised without accounts, credentials and DNS.

Customer registration creates no staff permissions. Provision a confirmed, dedicated Answerbase employee identity using the host-only command:

```sh
node scripts/provision-staff.mjs EMPLOYEE_UUID support
```

Use `platform_admin` for the other staff role, or `revoke` to disable a grant. The operator must verify employment separately; a matching email domain is not proof. Database constraints reject customer identities as staff. Both staff roles can act through audited, time-limited delegation, as requested by the owner. Neither role belongs to a client organization.

## Updates and rollback

Enable branch protection/review and required CI checks on `main` before enabling automatic host updates. Copy the `answerbase-update.service` and `.timer` files from `infra/deploy` to `/etc/systemd/system`, then enable the timer with `systemctl enable --now answerbase-update.timer`.

The updater fetches `main`, checks successful GitHub Actions results for the exact commit (backend, both UIs, connectors, evaluation contracts and deployment configuration), refuses to overwrite a dirty checkout, and deploys it. GitHub API failure or pending/failed checks stop deployment. No GitHub credential is necessary for the public repository. Review/merging stays in GitHub; the updater does not merge PRs. `main` checks always test both frontends and backend.

The manual SSH workflow is an alternative for a runner with network access from your allowed administrator address. Standard GitHub-hosted runners are **not** automatically permitted through the restricted SSH rule. Use the host updater as the default.

Before release, take a verified backup. If a deployment fails, preserve logs and restore the last application revision only when its schema is compatible. Flyway does not automatically reverse migrations. For an incompatible schema change, restore a verified database/originals backup into a separate environment and validate it before switching traffic. Never run `docker compose down -v` on a deployed instance.

## Monitoring and recovery

The monitor container checks API, worker, embeddings, reranker, queue failures/age, expired question leases, exhausted retries, pending deletion failures and recent p95 latency every minute. It emits aggregate JSON to container logs, without question text, user identities or secrets. Configure `MONITOR_WEBHOOK_URL` to deliver changes/recovery and hourly reminders to an HTTPS webhook you control. Without that endpoint, alerts remain local logs; external delivery is not claimed. Compose rotates logs to three 10 MB files per service.

```sh
node scripts/backup.mjs --production
```

A backup includes a PostgreSQL custom dump and every referenced original. The command restores into a new temporary database, verifies restored counts and each original's size/SHA-256, and only then writes a successful manifest. Missing or corrupted originals fail the backup. It never restores over the running database. `--database-only` deliberately excludes originals. Backups are private under `.cache/backups`; they contain customer data. Supabase Auth is a separate recovery dependency: preserve the project and configured identity/signing settings, and arrange provider-supported recovery before real customer use.

A daily systemd backup service/timer is supplied. Copy verified backups to encrypted off-host storage under your own account; a same-VM backup does not survive VM loss or reclamation. Keep only an explicitly chosen retention window and monitor disk space. Off-host upload, recovery from a lost VM, SMTP delivery, public TLS and hosted model performance require the real accounts and cannot be certified by local tests.

The application caps retained versions and bytes per workspace, **and** total stored document bytes across all organizations. Production defaults reserve headroom under the 1 GB bucket allowance (`DOCUMENT_TOTAL_MAX_BYTES=838860800`). Storage-provider overhead, pending deletions and other project usage still consume capacity. Keep billing disabled/free and monitor provider usage. Do not assume app counters can enforce a provider's billing policy.
