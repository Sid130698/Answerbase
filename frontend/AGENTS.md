# Frontend work

Read `../DESIGN.md` before changing UI. Its tokens are implemented in `src/styles/tokens.css`.
Keep these rules applicable to future Answerbase screens:

- Build around the actual user task and real implemented behavior.
- Use shared color, type, spacing, and motion tokens. No purple gradients, nested cards, fake metrics, or decorative dashboards.
- Prefer meaningful lists and tables to repeated feature cards. Keep typography and alignment consistent.
- Use semantic links/buttons, visible focus, labeled inputs, 44px touch targets, and reduced-motion support.
- Add loading, empty, error, and retry states when data is fetched. Persist navigable state in the URL.
- Do a critique pass at desktop and mobile widths; check keyboard interactions and actual service failures.
- Run `npm run lint` and `npm run build`. Follow existing package and framework choices.
- This is the customer product surface: Ask, Sources, and evidence. Never add Swagger, API references, build status, or repository links to customer navigation. Keep unimplemented actions clearly unavailable.
