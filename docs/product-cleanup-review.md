# Product cleanup review — 2026-11-27

Branch: `feature/prd-audit-and-product-cleanup`; PR #15, based on #14. This review fixes concrete findings and records PRD gaps. It is not a full security certification or PRD sign-off.

## Fixed

- Obsolete conversation/history requests could overwrite newer UI state. Requests now cancel on context changes and unmount.
- The API client overwrote caller abort signals. Both normal and delegated requests now combine caller cancellation with the existing timeout.
- Feedback notes/results could carry over to a different answer. Reset answer-specific feedback, cancel stale feedback requests, and prevent duplicate submissions.
- The entire negative-feedback form appeared on every answer. Show it only after “Not helpful.”
- Stream parsing assumed LF-only framing and leaked the reader on some failures. Handle fragmented UTF-8, CRLF and mixed line endings; always cancel/release the reader.
- Failed questions were disabled with no direct recovery action. Selecting one now restores its question to the composer for resubmission.
- Arbitrary conversation query parameters caused avoidable API errors. Validate UUID syntax before using the value.
- Non-admin links to admin views could show Sources while the title still said Access/Questions. Resolve these URLs to Ask consistently.
- Model/retention settings were buried under Access. Move them to a separate Settings page.
- Completed answers concealed source dates until a citation was selected. Display cited document versions and upload dates with the answer.
- Remove stale “Product preview” labels and the generic Ask headline; reduce mobile navigation and account-row height.
- Hardcoded upload quotas prevented realistic capacity configuration. Add validated deployment quotas, retain the small defaults, and check byte capacity without arithmetic overflow.
- nginx could buffer SSE and interrupt a quiet generation request at its default timeout. Disable buffering for customer/delegated answer streams and allow the API's existing deadline to expire first.
- Browser HTML caching could reopen old bundles after rebuilding containers. Revalidate both application shells while preserving security headers.
- The PRD checklist mixed old slice-level test counts and future work with completed claims. Replace it with a requirement-level record that explicitly identifies missing behavior and acceptance evidence.

## Validation

- 25 Java tests passed, zero errors/failures/skips; shared-core line coverage 566/726 (77.96%).
- Customer and staff frontend lint/typecheck/production builds passed.
- Three stream regression tests and ten connector/evaluation contract tests passed. Stream tests run in CI.
- Browser: actual local cited answer, source update date, feedback disclosure/reset, separate Settings, desktop layout and 390px layout. Both nginx configurations pass `nginx -t`; both HTML responses return `Cache-Control: no-cache` with security headers intact.
- Fresh restricted-document probe: authorized admin retrieved the canary; viewer retrieval excluded it; original returned 404; adversarial answer excluded it and abstained. Probe removed its temporary document/membership.
- Fresh request through the customer nginx proxy: first status 299ms; first verified claim 6,198ms; final answer 6,200ms. This verifies delivery through the proxy, not a percentile benchmark. The first-answer 1.5s target remains missed; status is not answer text.

This is the historical PR #15 review. PR #16 subsequently adds live model CI, the generation retry queue, representative public corpora, real ingestion/concurrency testing and deployment operations. See [current PRD coverage](prd-checklist.md) for their measured acceptance state. Human calibration, hosted account verification and genuine external participants remain separate gates. Nothing was merged by this cleanup.
