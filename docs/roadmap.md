# Delivery status and acceptance gates

The implemented product path includes authentication, organization/workspace enrollment, members/groups, five-format ingestion, private originals/version history, folder sync, authorized hybrid retrieval/reranking, incrementally verified cited answers/history, source inspection, feedback/corrections, query administration, settings, retention/deletion, and a separate staff console with audited delegation. Provider failures now queue bounded automatic retries.

Release infrastructure includes Terraform/cloud-init, a production Compose/TLS topology, deployment preflights, a checked-main host updater, continuous monitoring and verified database/original backups. CI includes real local-model baseline/candidate evaluation on a representative PR subset; manual workflow runs evaluate all 70 cases without hosted model credentials. The benchmark now includes 2,000 actual uploads / 50,000 actual embeddings, a pinned public handbook and three NIST PDFs. See [the acceptance record](prd-checklist.md) for measured outcomes, not just script availability.

Remaining external acceptance work:

1. Approve/correct the draft gold questions, answers, anchors and difficulty labels. Independently grade the blind 20% sample and measure judge agreement. AI-generated grades cannot supply the human evidence.
2. Configure actual free-tier accounts, DNS, sender-domain/SMTP, provider retention settings, private storage and Groq. Verify public HTTPS, email confirmation/recovery, upload/answer/source access and staff separation on the real host.
3. Store encrypted verified backups off-host, verify Auth recovery and restore onto a replacement host. Local restore verification alone does not prove disaster recovery.
4. Recruit five genuine independent testers, obtain three consented quotes and conduct a pilot follow-up. Use the [tester worksheet](tester-session.html); do not fabricate participants or opinions.
5. Review PRs and required checks before merging, configure protected main, then enable the host deployment timer. No unreviewed feature branch is automatically deployed.

Measured quality/performance misses must be reported and investigated, even when the implementation exists. The [walkthrough](walkthrough.html) and operations material are supplied; a public URL cannot exist before hosting and DNS are configured. OCR remains stretch scope. Company SSO/domain enrollment and tenant hostnames remain future adapters, per owner direction.
