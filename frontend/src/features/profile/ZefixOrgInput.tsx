import { useEffect, useRef, useState } from "react"

import { Input } from "@/components/ui/input"
import { useLazySearchZefixQuery } from "@/lib/api"

/**
 * Employer / organization field with Handelsregister (Zefix) search-as-you-type.
 * Picking a hit fills the officially registered company name — this does NOT
 * create an organization entity, it just uses the Zefix data as the person's
 * workplace label (which also lets the Person↔Org link resolve to a matching
 * org entity when one exists). Free text is always allowed.
 */
export function ZefixOrgInput({
  value,
  onChange,
  id,
  required,
}: {
  value: string
  onChange: (name: string) => void
  id?: string
  required?: boolean
}) {
  const [open, setOpen] = useState(false)
  const [search, { data: hits, isFetching }] = useLazySearchZefixQuery()
  const debounce = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  useEffect(() => () => clearTimeout(debounce.current), [])

  const onType = (text: string) => {
    onChange(text)
    setOpen(true)
    clearTimeout(debounce.current)
    if (text.trim().length >= 3) {
      debounce.current = setTimeout(() => void search(text.trim()), 350)
    }
  }

  const pick = (name: string) => {
    onChange(name)
    setOpen(false)
  }

  const showList = open && value.trim().length >= 3 && (hits?.length ?? 0) > 0

  return (
    <div className="relative">
      <Input
        id={id}
        required={required}
        placeholder="Firma suchen (Handelsregister) oder frei eingeben…"
        value={value}
        autoComplete="off"
        onChange={(e) => onType(e.target.value)}
        onFocus={() => value.trim().length >= 3 && setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
      />
      {showList && (
        <div className="absolute inset-x-0 top-full z-10 mt-1 overflow-hidden rounded-md border bg-popover shadow-md">
          {hits!.map((hit) => (
            <button
              key={hit.uid || hit.name}
              type="button"
              className="flex w-full items-baseline justify-between gap-3 px-3 py-2 text-left text-sm hover:bg-accent"
              // onMouseDown fires before the input's onBlur, so the pick registers.
              onMouseDown={(e) => {
                e.preventDefault()
                pick(hit.name)
              }}
            >
              <span className="truncate font-medium">{hit.name}</span>
              <span className="shrink-0 text-xs text-muted-foreground">
                {hit.legalSeat}
                {hit.canton ? ` ${hit.canton}` : ""}
              </span>
            </button>
          ))}
        </div>
      )}
      <p className="mt-1 text-xs text-muted-foreground">
        {isFetching
          ? "Suche im Handelsregister…"
          : "Abgleich mit dem Handelsregister (Zefix) — oder frei eingeben."}
      </p>
    </div>
  )
}
