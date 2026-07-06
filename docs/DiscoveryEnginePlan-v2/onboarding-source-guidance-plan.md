# Add "how to obtain this source" guidance to onboarding cards

## Context

During onboarding, users link their sources (GitHub, ORCID, LinkedIn, Website) so
Orbit can generate their profile. Today the source cards give **no pointers on how
to actually obtain each identifier** — e.g. where to find an ORCID iD, or how to
export a LinkedIn profile as PDF. Non-technical users get stuck at exactly the step
that decides whether a profile can be built at all.

There is already a `helper` field on `SourceDef`, but it (a) only renders on the
*collapsed* card and (b) is only set for the company website. We want short,
actionable how-to guidance shown **while the user is filling in the input** (i.e. in
the expanded card), with an optional deep link to the right page.

**Decision (confirmed with user):** present the guidance *inline in the expanded
card*, above the input — matching the existing hand-rolled disclosure pattern. No
new ShadCN/Radix primitive (Popover/Tooltip) is introduced.

## Files to change

All under `frontend/src/features/ingestion/`:

1. **`types.ts`** — add a structured guide type to `SourceDef`.
2. **`sourceDefs.ts`** — author the German guidance per source.
3. **`SourceCard.tsx`** — render the guide inside the open block, above the input.

No backend changes. No changes needed in `SourcesStep.tsx` (it already spreads each
`def` into `SourceCard`).

## Step 1 — `types.ts`

Add a `SourceGuide` interface and a `guide?` field on `SourceDef` (keep the existing
`helper` field as-is; it serves a different, collapsed-state purpose):

```ts
export interface SourceGuide {
  /** One-line intro, e.g. "So findest du deine ORCID iD:". */
  intro: string
  /** Ordered how-to steps, rendered as a compact numbered list. */
  steps: string[]
  /** Optional deep link to the right page (opens in a new tab). */
  link?: { label: string; href: string }
}

export interface SourceDef {
  kind: SourceKind
  title: string
  hint: string
  glyph: string
  placeholder?: string
  upload?: boolean
  /** Explanatory line shown under the collapsed card. */
  helper?: string
  /** Step-by-step "how to obtain this" guidance, shown in the expanded card. */
  guide?: SourceGuide
}
```

## Step 2 — `sourceDefs.ts`

Add a `guide` to each active source. Draft copy (German, per project default;
refine wording during implementation):

- **GITHUB_SOURCE** — intro: "So findest du deinen GitHub-Username:"; steps: auf
  github.com einloggen; Username steht oben rechts im Profilmenü und in der Profil-URL
  (`github.com/<username>`). link: `{ label: "GitHub öffnen", href: "https://github.com" }`.
- **ORCID_SOURCE** — intro: "So findest du deine ORCID iD:"; steps: auf orcid.org
  registrieren/einloggen; die 16-stellige iD steht links im Profil (Form
  `0000-0002-1825-0097`). link: `{ label: "orcid.org öffnen", href: "https://orcid.org" }`.
- **LINKEDIN_SOURCE** — intro: "So exportierst du dein LinkedIn-Profil als PDF:";
  steps: eigenes Profil öffnen; auf "Mehr" / "More" klicken; "Als PDF speichern" /
  "Save to PDF" wählen; die heruntergeladene PDF hier hochladen. link:
  `{ label: "LinkedIn öffnen", href: "https://www.linkedin.com/in/" }`. (Most valuable
  guide — the export flow is non-obvious.)
- **WEBSITE_SOURCE** — intro: "Welche Website?"; steps: deine persönliche Seite,
  Portfolio oder Blog; ganze URL inkl. Domain (`www.…`). No link.
- **COMPANY_WEBSITE_SOURCE** — keep existing `helper`; optionally add a short `guide`
  (intro "Welche Website?"; step: offizielle Firmenwebsite, wir lesen sie aus). Low
  priority.

## Step 3 — `SourceCard.tsx`

Render the guide at the top of the existing open block (`{open && !added && (…)}`,
currently lines 112–157), before the input/upload row. Keep the current muted,
`text-xs` styling to match the codebase. Sketch:

```tsx
{open && !added && (
  <div className="border-t">
    {def.guide && (
      <div className="space-y-1 px-4 pt-3 text-xs text-muted-foreground">
        <p className="font-medium text-foreground">{def.guide.intro}</p>
        <ol className="list-decimal space-y-0.5 pl-4">
          {def.guide.steps.map((s, i) => (
            <li key={i}>{s}</li>
          ))}
        </ol>
        {def.guide.link && (
          <a
            href={def.guide.link.href}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1 text-primary hover:underline"
          >
            {def.guide.link.label}
            <ExternalLink className="size-3" />
          </a>
        )}
      </div>
    )}
    <div className="flex gap-2 px-4 py-3">
      {/* existing input / PDF picker unchanged */}
    </div>
  </div>
)}
```

Notes:
- Import `ExternalLink` from `lucide-react` (already the icon lib in this file).
- Move the `border-t` from the inner input row up to the wrapper so the guide + input
  read as one panel (avoid a double top border). Verify spacing when guide is absent
  (e.g. website with no link) so there's no empty gap.
- The LinkedIn (`upload`) branch keeps its PDF picker; the guide sits above it — this
  is where the export steps matter most.

## Verification

1. `cd frontend && npm run typecheck` — confirms the new `SourceGuide` type wiring.
2. `npm run dev`, go through onboarding to the sources step (`StartPage` →
   `?mode=supply`, choose Person). Expand **GitHub**, **ORCID**, **LinkedIn**,
   **Website** and confirm each shows its numbered how-to + working external link,
   directly above the input.
3. Check the LinkedIn card: guide appears above the "PDF auswählen…" button.
4. Confirm collapsed cards are unchanged and the company-website `helper` still shows
   on its collapsed card (org supply flow).
5. `npm run lint` and `npm run format` before finishing (Prettier: no semicolons,
   double quotes).
