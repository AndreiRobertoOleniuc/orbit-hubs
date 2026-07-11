import { useState } from "react"
import { Link } from "react-router-dom"

import { Button } from "@/components/ui/button"
import { ProfileSkeleton } from "@/components/ProfileSkeleton"
import { OwnProfileView } from "@/features/profile/OwnProfileView"
import { PublicProfileView } from "@/features/profile/PublicProfileView"
import { useGetMyEntitiesQuery } from "@/lib/api"
import { cn } from "@/lib/utils"

/** Own profile at /profile (auth-guarded route). */
export function OwnProfilePage() {
  const { data: entities, isLoading, error } = useGetMyEntitiesQuery()
  const [view, setView] = useState<"privat" | "oeffentlich">("privat")
  const [selectedId, setSelectedId] = useState<number | null>(null)

  if (isLoading) return <ProfileSkeleton />

  if (error) {
    return (
      <div className="mx-auto max-w-xl py-16 text-center">
        <h1 className="text-2xl font-semibold">
          Profil konnte nicht geladen werden
        </h1>
        <p className="mt-2 text-muted-foreground">
          Bitte versuch es später erneut.
        </p>
      </div>
    )
  }

  if (!entities || entities.length === 0) {
    return (
      <div className="mx-auto max-w-xl py-16 text-center">
        <h1 className="text-2xl font-semibold">Noch kein Profil</h1>
        <p className="mx-auto mt-2 max-w-md text-muted-foreground">
          Verlinke deine Quellen — GitHub, ORCID, LinkedIn oder deine Website —
          und wir bauen dein Profil automatisch für dich.
        </p>
        <Button asChild className="mt-6 min-w-52">
          <Link to="/onboarding?mode=supply">Profil erstellen</Link>
        </Button>
      </div>
    )
  }

  const entity = entities.find((e) => e.id === selectedId) ?? entities[0]

  // Demand profiles have no public preview (spec §5.1) — force the private view
  // and hide the Ansicht toggle.
  const supply = entity.profileMode !== "demand"
  const effectiveView = supply ? view : "privat"

  return (
    <div className="mx-auto max-w-2xl py-4">
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        {entities.length > 1 ? (
          <div className="flex flex-wrap gap-1.5">
            {entities.map((e) => (
              <button
                key={e.id}
                type="button"
                className={cn(
                  "rounded-full border px-3 py-1 text-sm transition-colors",
                  e.id === entity.id
                    ? "border-primary bg-primary/10 font-medium"
                    : "text-muted-foreground hover:bg-accent"
                )}
                onClick={() => setSelectedId(e.id)}
              >
                {e.displayName}
              </button>
            ))}
          </div>
        ) : (
          <span />
        )}
        {supply ? (
          <div className="flex items-center gap-2">
            <span className="text-sm text-muted-foreground">Ansicht</span>
            <div className="flex rounded-lg border p-0.5">
              {(["privat", "oeffentlich"] as const).map((v) => (
                <button
                  key={v}
                  type="button"
                  className={cn(
                    "rounded-md px-3 py-1 text-sm transition-colors",
                    view === v
                      ? "bg-secondary font-medium"
                      : "text-muted-foreground hover:text-foreground"
                  )}
                  onClick={() => setView(v)}
                >
                  {v === "privat" ? "Privat" : "Öffentlich"}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <span />
        )}
      </div>

      {effectiveView === "privat" ? (
        <OwnProfileView entity={entity} />
      ) : (
        <PublicProfileView entity={entity} />
      )}
    </div>
  )
}
