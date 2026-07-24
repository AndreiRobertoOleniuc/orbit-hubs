import { useState } from "react"
import { Link, Navigate } from "react-router-dom"
import { Eye, Pencil } from "lucide-react"

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
        <Button asChild className="mt-6 w-full sm:w-auto sm:min-w-52">
          <Link to="/onboarding?mode=supply">Profil erstellen</Link>
        </Button>
      </div>
    )
  }

  const entity = entities.find((e) => e.id === selectedId) ?? entities[0]

  if (entity.status === "draft") {
    return <Navigate to={`/onboarding?mode=${entity.profileMode}`} replace />
  }

  // Demand profiles have no public preview (spec §5.1) — force the private view
  // and hide the Ansicht toggle.
  const supply = entity.profileMode !== "demand"
  const effectiveView = supply ? view : "privat"

  return (
    <div className="mx-auto max-w-2xl py-4">
      <div className="mb-6 flex flex-col items-stretch gap-3 sm:flex-row sm:flex-wrap sm:items-center sm:justify-between">
        {entities.length > 1 ? (
          <div className="flex max-w-full gap-1.5 overflow-x-auto pb-1 sm:flex-wrap sm:overflow-visible sm:pb-0">
            {entities.map((e) => (
              <button
                key={e.id}
                type="button"
                className={cn(
                  "shrink-0 rounded-full border px-3 py-1 text-sm transition-colors",
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
          effectiveView === "privat" ? (
            <Button
              className="w-full sm:w-auto"
              variant="outline"
              onClick={() => setView("oeffentlich")}
            >
              <Eye className="size-4" /> So sehen dich andere
            </Button>
          ) : (
            <Button
              className="w-full sm:w-auto"
              onClick={() => setView("privat")}
            >
              <Pencil className="size-4" /> Profil bearbeiten
            </Button>
          )
        ) : (
          <span />
        )}
      </div>

      {effectiveView === "privat" ? (
        <OwnProfileView entity={entity} />
      ) : (
        <>
          <div className="mb-5 flex items-center gap-2 rounded-lg border border-primary/20 bg-primary/5 px-4 py-3 text-sm">
            <Eye className="size-4 shrink-0 text-primary" />
            <span>
              <strong className="font-medium text-foreground">Vorschau</strong>{" "}
              <span className="text-muted-foreground">
                — so erscheint dein Profil in Suche, Board und für andere.
              </span>
            </span>
          </div>
          <PublicProfileView entity={entity} />
        </>
      )}
    </div>
  )
}
