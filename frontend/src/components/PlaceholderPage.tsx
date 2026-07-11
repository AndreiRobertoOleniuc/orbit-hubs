import type { ReactNode } from "react"

/**
 * A simple "coming in a later stage" placeholder for mode surfaces that aren't
 * built yet (search, board, requests, inbox). Keeps the navigation layout
 * complete so both modes are fully walkable.
 */
export function PlaceholderPage({
  title,
  subtitle,
  icon,
}: {
  title: string
  subtitle: string
  icon?: ReactNode
}) {
  return (
    <div className="mx-auto max-w-xl py-20 text-center">
      {icon && (
        <div className="mx-auto mb-4 flex size-12 items-center justify-center rounded-xl border bg-muted text-muted-foreground">
          {icon}
        </div>
      )}
      <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
      <p className="mx-auto mt-2 max-w-md text-muted-foreground">{subtitle}</p>
      <p className="mt-6 inline-block rounded-full border px-3 py-1 text-xs text-muted-foreground">
        In Arbeit — folgt in einer späteren Stufe
      </p>
    </div>
  )
}
