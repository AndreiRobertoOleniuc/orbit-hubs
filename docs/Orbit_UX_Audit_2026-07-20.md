# Orbit UX/UI audit — 20 July 2026

## Scope

Evidence: 32 desktop screenshots supplied in `/Users/andreioleniuc/Desktop/MVP Screenshots`, covering logged-out, authenticated demand, authenticated supply, onboarding, search, public requests, profiles, inbox states, and contact dialogs.

Goal: a newcomer should understand Orbit and use the complete product without guidance.

Limit: no mobile screenshots and no live interaction, keyboard, screen-reader, loading-duration, or responsive-reflow testing were available.

## Overall verdict

Orbit's core product flow is understandable and visually consistent. Search evidence, public requests, profile tabs, and inbox tabs are the strongest surfaces. The main weakness is not missing UI; it is too much explanatory copy, inconsistent terminology/language, and several data labels that reduce trust.

## P0 — fix before further polish

1. Replace `Manual Entry` with the person's actual display name. It currently reads like an internal ingestion label exposed to users.
2. Keep generated user-facing content German by default. German navigation surrounding English summaries and request descriptions feels unfinished and contradicts the German-default product requirement.
3. Localize Clerk sign-in and sign-up to German.
4. Correct source dates in evidence lists. A publication titled `(2007)` displayed with `2026` on the right is ambiguous and looks incorrect. Label ingestion dates explicitly or display the publication year.
5. Move the landing's two main choices above the three-step explanation. At an ordinary laptop height, the primary actions fall below the first viewport.

## P1 — high-impact comprehension improvements

1. Rewrite the landing in plain German. Remove the repeated em-dash constructions and the inaccurate implication that LinkedIn is automatically fetched as a public source.
2. Shorten the landing to one value proposition, two choices, and a compact three-step explanation below.
3. Replace `Ich biete` with `Ich möchte gefunden werden` or `Profil sichtbar`, because the supply side does not actively bid.
4. Replace repeated `So kommst du an diese Quelle — hier klicken` copy with source-specific actions:
   - `GitHub-Benutzernamen finden`
   - `ORCID iD finden`
   - `LinkedIn-Profil als PDF exportieren`
   - `Website-Adresse eingeben`
5. Shorten inbox contact cards. Show identity, request/search context, message, status, and action. Hide the full profile summary behind `Profil ansehen`.
6. Rename contact status `Offen` to `Wartet auf Antwort`; it is clearer in the inbox context.
7. Change exposed email buttons to an explicit action such as `E-Mail schreiben`, rather than styling the raw address as the primary CTA.
8. Simplify the `So funktioniert Orbit` page. Keep the process and a short FAQ; move the full glossary into collapsible entries or remove definitions that the interface no longer needs.

## P2 — structure and polish

1. Turn the small profile pencil icon into a labelled `Profilinformationen bearbeiten` action.
2. On the public-profile preview, make the preview state persistent and visually distinct; avoid presenting a working self-contact CTA as if it were a normal action.
3. Replace the Hub explanation's abstract `Kreis` wording with: `Ein Hub ist ein Netzwerk oder eine Institution, der du angehörst. Orbit sucht zuerst innerhalb dieses Netzwerks.`
4. Keep the profile tabs. They materially reduce scrolling and group related tasks well.
5. Keep the Postfach tabs. They are clearer than the former stacked sections.
6. Keep `Warum dieser Treffer` and `Belege` as the main hierarchy on search results. This is Orbit's strongest and most distinctive UI pattern.
7. Reduce excessive vertical whitespace on empty and single-result pages by using purposeful empty-state guidance or a slightly denser page frame; do not fill space with extra explanatory paragraphs.

## Screen health

1. Landing: **needs work** — clear purpose, but too much generated-sounding copy and actions too low.
2. How it works: **needs simplification** — useful reference, but reads like documentation and is too long.
3. Search form: **healthy** — focused and understandable; helper copy can be shortened.
4. Search loading/results: **healthy with trust fixes** — excellent match rationale; source/date presentation needs correction.
5. Public request board/detail: **mostly healthy** — scannable and understandable; language consistency remains a problem.
6. Authentication: **needs work** — English Clerk UI breaks the German product experience.
7. Onboarding: **mixed** — type selection is clear; source guidance is repetitive; completion screen is too long.
8. Profile management: **mixed** — tabs and preview are improvements; identity and editing affordances need correction.
9. Inbox/contact: **mixed** — tabs and request context are strong; cards are too verbose and status/action wording can improve.

## Accessibility risks visible from screenshots

- Small muted helper text may be low contrast, especially source hints, metadata, and footer text.
- Several icon-only controls require accessible names and visible focus states; screenshots cannot verify either.
- Tooltips must not contain information required to complete a task, especially on touch devices.
- Disabled mint buttons can be difficult to distinguish from enabled actions without checking contrast and state semantics.
- No mobile, zoom, keyboard, screen-reader, or reduced-motion evidence was supplied.

## Recommended execution order

1. Identity, language, authentication localization, and evidence-date trust fixes.
2. Landing rewrite and reordered hierarchy.
3. Source-specific onboarding copy.
4. Inbox card reduction and clearer statuses/actions.
5. Profile edit/preview refinements.
6. Contrast, keyboard, mobile, and assistive-technology verification.
