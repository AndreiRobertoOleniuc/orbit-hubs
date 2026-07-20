import { useEffect, useState } from "react"
import { Loader2, MapPin, X } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import type { FirmDraft } from "@/features/onboarding/types"
import { useLazySearchZefixQuery } from "@/lib/api"

interface ZefixFirmSearchProps {
  firm: FirmDraft | null
  onSelect: (firm: FirmDraft | null) => void
  /** Called when a Zefix hit is confirmed ("Das ist meine Firma") or a manual firm is committed. */
  onConfirm?: () => void
}

/**
 * Handelsregister search-as-you-type against GET /api/v1/zefix/search, with a
 * manual fallback when the company is not found (or Zefix is not configured).
 */
export function ZefixFirmSearch({
  firm,
  onSelect,
  onConfirm,
}: ZefixFirmSearchProps) {
  const [query, setQuery] = useState("")
  const [manual, setManual] = useState(false)
  const [manualName, setManualName] = useState("")
  const [manualLoc, setManualLoc] = useState("")
  const [search, { data: hits, isFetching }] = useLazySearchZefixQuery()

  useEffect(() => {
    const term = query.trim()
    if (term.length < 3 || firm) return
    const t = setTimeout(() => search(term), 350)
    return () => clearTimeout(t)
  }, [query, firm, search])

  if (firm) {
    return (
      <div className="space-y-4">
        <Card className="relative">
          <CardContent className="space-y-3 pt-2">
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              className="absolute top-2 right-2"
              aria-label="Firma entfernen"
              onClick={() => onSelect(null)}
            >
              <X />
            </Button>
            <div>
              <p className="text-base font-semibold">{firm.name}</p>
              {firm.uid && (
                <p className="text-sm text-muted-foreground">{firm.uid}</p>
              )}
            </div>
            <div className="flex flex-wrap gap-2">
              {firm.legalSeat && (
                <Badge variant="secondary">
                  <MapPin className="size-3" /> {firm.legalSeat}
                  {firm.canton ? ` ${firm.canton}` : ""}
                </Badge>
              )}
              {firm.legalForm && (
                <Badge variant="secondary">{firm.legalForm}</Badge>
              )}
              {firm.manual && <Badge variant="outline">manuell erfasst</Badge>}
            </div>
            {onConfirm && (
              <Button type="button" className="w-full" onClick={onConfirm}>
                Das ist meine Firma
              </Button>
            )}
          </CardContent>
        </Card>
      </div>
    )
  }

  if (manual) {
    return (
      <div className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="firm-name">Firmenname</Label>
          <Input
            id="firm-name"
            autoFocus
            placeholder="z.B. Muster Technik GmbH"
            value={manualName}
            onChange={(e) => setManualName(e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="firm-loc">Ort</Label>
          <Input
            id="firm-loc"
            placeholder="z.B. Baden AG"
            value={manualLoc}
            onChange={(e) => setManualLoc(e.target.value)}
          />
        </div>
        <div className="grid gap-2 sm:flex">
          <Button
            className="w-full sm:w-auto"
            type="button"
            disabled={!manualName.trim()}
            onClick={() => {
              onSelect({
                name: manualName.trim(),
                legalSeat: manualLoc.trim() || undefined,
                manual: true,
              })
            }}
          >
            Übernehmen
          </Button>
          <Button
            className="w-full sm:w-auto"
            type="button"
            variant="ghost"
            onClick={() => setManual(false)}
          >
            Zurück zur Suche
          </Button>
        </div>
      </div>
    )
  }

  const term = query.trim()

  return (
    <div className="space-y-3">
      <div className="relative">
        <Input
          autoFocus
          placeholder="Firma suchen…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        {isFetching && (
          <Loader2 className="absolute top-1/2 right-3 size-4 -translate-y-1/2 animate-spin text-primary" />
        )}
        {term.length >= 3 && isFetching && (
          <div
            className="absolute inset-x-0 top-full z-10 mt-1 space-y-3 rounded-md border bg-popover p-3 shadow-md"
            aria-label="Handelsregister wird durchsucht"
          >
            {["first", "second", "third"].map((row, index) => (
              <div
                key={row}
                className="flex items-center justify-between gap-4"
              >
                <Skeleton
                  className={`h-4 ${index === 1 ? "w-1/2" : "w-3/5"}`}
                />
                <Skeleton className="h-3 w-16" />
              </div>
            ))}
          </div>
        )}
        {term.length >= 3 && !isFetching && (hits?.length ?? 0) > 0 && (
          <div className="absolute inset-x-0 top-full z-10 mt-1 overflow-hidden rounded-md border bg-popover shadow-md">
            {hits!.map((hit) => (
              <button
                key={hit.uid || hit.name}
                type="button"
                className="flex w-full flex-col items-start gap-0.5 px-3 py-2.5 text-left text-sm hover:bg-accent sm:flex-row sm:items-baseline sm:justify-between sm:gap-3"
                onClick={() => {
                  onSelect({
                    name: hit.name,
                    uid: hit.uid || undefined,
                    legalSeat: hit.legalSeat || undefined,
                    canton: hit.canton || undefined,
                    legalForm: hit.legalForm || undefined,
                  })
                  setQuery("")
                }}
              >
                <span className="truncate font-medium">{hit.name}</span>
                <span className="text-xs text-muted-foreground sm:shrink-0">
                  {hit.legalSeat}
                </span>
              </button>
            ))}
          </div>
        )}
      </div>
      <p className="text-xs text-muted-foreground">
        {isFetching
          ? "Wir gleichen den Namen mit dem Schweizer Handelsregister ab…"
          : "Abgleich mit dem Handelsregister (Zefix). Mindestens 3 Zeichen eingeben."}
      </p>
      <Button
        type="button"
        variant="ghost"
        size="sm"
        onClick={() => {
          setManual(true)
          setManualName(term)
        }}
      >
        Manuell erfassen
      </Button>
    </div>
  )
}
