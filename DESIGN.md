# Answerbase interface system

## Product and surface

Answerbase is a document Q&A workspace for small support and operations teams. Its visual identity should prioritize reading, source verification, and clear access boundaries.
The customer UI centers on asking questions, checking cited passages, and managing sources. Swagger, API catalogs, readiness checks, repository links, and implementation milestones belong in developer tooling, never customer navigation.
Until uploads and retrieval are implemented, show an honest product preview with empty states and clearly unavailable actions. Never fabricate documents, answers, citations, user identities, or successful uploads.

## Direction

Use a quiet document-workspace layout: a narrow navigation rail, a clear page heading, a question composer, a source library, and a contextual evidence panel.
A sidebar supports task switching. Content is left-aligned and grouped with space and rules. Avoid nested cards, ornamental metrics, decorative numbering, gradients, and oversized marketing headlines.
Keep ordinary app affordances familiar; do not borrow full-screen marketing choreography for an operational tool.

## Tokens

CSS source of truth: `frontend/src/styles/tokens.css`.

| Role | Value |
| --- | --- |
| Main surface | `#ffffff` |
| Navigation surface | `#f4f6f9` |
| Primary text | `#202c3f` |
| Secondary text | `#596579` |
| Accent / focus | `#2855b7` |
| Rules | `#dce2eb` |
| Success | `#256442` |
| Error | `#a13832` |

Typography: self-hosted IBM Plex Sans, weights 400 / 500 / 600. Body 16px; controls and labels 14px; secondary metadata 13px; page heading 32px.
Use monospace only for actual HTTP methods, endpoint paths, and code. Use tabular numerals for changing measurements.
Spacing scale: 4, 8, 12, 16, 24, 32, 48px. Buttons and controls have at least 44px target height.
Use 6px control corners; 10px for a rare contained notice. No default shadows.

## Interaction and motion

Use native links for navigation and buttons for actions. Keep page selection in the URL.
Use explicit loading, retry, empty, and failure states. API availability is fetched; no fake counts or successful responses.
Focus rings are always visible for keyboard navigation. Include a skip link and semantic headings/tables.
Use 160ms ease-out transitions for button feedback; no page entrance choreography, bouncing controls, or looping decoration.
Reduced motion removes movement. Hover-specific feedback only applies to hover-capable pointers.
Mobile collapses the rail into a top navigation strip. Tables may scroll within their own labeled region; the page must not overflow.

## Review before shipping

1. Test desktop and narrow mobile layouts with real content.
2. Check keyboard focus, links, labels, contrast, and reduced motion.
3. Exercise loading, success, request failure, retry, and empty search states.
4. Ensure the interface serves customer tasks; keep developer tooling separate.
5. Remove decoration or copy that does not help the current task.

## Requested guidance sources

These upstream sources were reviewed for this change. Their applicable principles are synthesized here; the plugins and their bundled automation tools are not installed in the repo.
When sources conflict, the user’s brief and the requirements of an operational app take precedence.

- [Impeccable](https://github.com/pbakaus/impeccable): product context, Operate-mode restraint, critique, and shared design tokens.
- [Taste Skill](https://github.com/Leonxlnx/taste-skill): coherent art direction and genuine product content; marketing effects are outside this surface’s scope. Project dials: variation 3/10, motion 2/10, density 6/10.
- [Anthropic frontend-design](https://github.com/anthropics/skills/tree/main/skills/frontend-design): explicit typography, palette, layout, and a second critique pass.
- [UI/UX Pro Max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill): accessibility-first hierarchy, touch targets, state coverage, and responsive layout. The bundled catalog/search tool was not executed.
- [Emil design engineering](https://github.com/emilkowalski/skills/tree/main/skills/emil-design-eng): restrained, interruptible feedback and reduced-motion support.
- [Vercel web-design-guidelines](https://github.com/vercel-labs/agent-skills/tree/main/skills/web-design-guidelines): semantic controls, keyboard focus, readable states, and browser-native navigation.
