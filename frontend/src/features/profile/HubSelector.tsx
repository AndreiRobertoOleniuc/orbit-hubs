import { useState } from "react"
import { Loader2 } from "lucide-react"

import { Button } from "@/components/ui/button"
import { useGetHubsQuery, useUpdateEntityHubsMutation } from "@/lib/api"
import { cn } from "@/lib/utils"
import type { EntityDto } from "@/types/api"

/**
 * Lets the owner pick which hub(s) their profile belongs to. Hubs come from the
 * public /hubs vocabulary; the confirmed set is saved via PUT /entities/{id}/hubs.
 */
export function HubSelector({ entity }: { entity: EntityDto }) {
  const { data: hubs, isLoading } = useGetHubsQuery()
  const [selected, setSelected] = useState<number[]>(
    entity.hubs.map((h) => h.id)
  )
  const [saved, setSaved] = useState(false)
  const [updateHubs, { isLoading: isSaving }] = useUpdateEntityHubsMutation()

  const toggle = (id: number) =>
    setSelected((prev) =>
      prev.includes(id) ? prev.filter((h) => h !== id) : [...prev, id]
    )

  const save = async () => {
    setSaved(false)
    await updateHubs({ entityId: entity.id, hubIds: selected }).unwrap()
    setSaved(true)
    setTimeout(() => setSaved(false), 3000)
  }

  if (isLoading) {
    return (
      <div className="flex justify-center py-4">
        <Loader2 className="size-5 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (!hubs || hubs.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        Noch keine Hubs verfügbar.
      </p>
    )
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        Wähle die Hubs, denen du angehörst — sie bestimmen, in welchem Umkreis
        dein Profil gefunden wird.
      </p>
      <div className="flex flex-wrap gap-2">
        {hubs.map((hub) => {
          const active = selected.includes(hub.id)
          return (
            <button
              key={hub.id}
              type="button"
              onClick={() => toggle(hub.id)}
              className={cn(
                "rounded-full border px-3 py-1 text-sm transition-colors",
                active
                  ? "border-primary bg-primary/10 text-primary"
                  : "border-input text-muted-foreground hover:bg-muted"
              )}
            >
              {hub.name}
              {hub.regionName && (
                <span className="ml-1 opacity-60">· {hub.regionName}</span>
              )}
            </button>
          )
        })}
      </div>
      <div className="flex items-center justify-end gap-3">
        {saved && (
          <span className="text-xs text-muted-foreground">✓ gespeichert</span>
        )}
        <Button size="sm" onClick={() => void save()} disabled={isSaving}>
          Speichern
        </Button>
      </div>
    </div>
  )
}
