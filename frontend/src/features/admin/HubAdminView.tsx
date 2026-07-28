import { useMemo, useState } from "react"
import { Loader2, Pencil, Plus, Trash2, X } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { RegionInput } from "@/features/admin/RegionInput"
import type { RegionSelection } from "@/features/admin/RegionInput"
import { ProfileSectionCard } from "@/features/profile/ProfileSectionCard"
import {
  useCreateHubMutation,
  useDeleteHubMutation,
  useGetAdminRegionsQuery,
  useGetHubsQuery,
  useUpdateHubMutation,
} from "@/lib/api"
import type { HubDto } from "@/types/api"

/**
 * Admin management of the hub vocabulary — create, edit, and delete hubs,
 * grouped by region. A hub attaches to an existing region or to any Swiss place
 * picked from the geo search, which is added to the region tree on demand.
 */
export function HubAdminView() {
  const { data: hubs, isLoading } = useGetHubsQuery()
  const { data: regions } = useGetAdminRegionsQuery()

  const [name, setName] = useState("")
  const [hubType, setHubType] = useState("")
  const [region, setRegion] = useState<RegionSelection | null>(null)
  const [editingId, setEditingId] = useState<number | null>(null)

  const [createHub, { isLoading: creating }] = useCreateHubMutation()
  const [updateHub] = useUpdateHubMutation()
  const [deleteHub] = useDeleteHubMutation()

  const byRegion = useMemo(() => {
    const groups = new Map<string, HubDto[]>()
    for (const hub of hubs ?? []) {
      const key = hub.regionName ?? "Ohne Region"
      groups.set(key, [...(groups.get(key) ?? []), hub])
    }
    return [...groups.entries()]
  }, [hubs])

  const resetForm = () => {
    setName("")
    setHubType("")
    setRegion(null)
    setEditingId(null)
  }

  const submit = async () => {
    if (!name.trim() || !region?.name.trim()) return
    const body = {
      name: name.trim(),
      hubType: hubType.trim() || null,
      regionId: region.regionId,
      regionName: region.regionId == null ? region.name.trim() : null,
      regionLatitude: region.latitude,
      regionLongitude: region.longitude,
    }
    if (editingId != null) {
      await updateHub({ id: editingId, body }).unwrap()
    } else {
      await createHub(body).unwrap()
    }
    resetForm()
  }

  const startEdit = (hub: HubDto) => {
    setEditingId(hub.id)
    setName(hub.name)
    setHubType(hub.hubType ?? "")
    setRegion({
      regionId: hub.regionId,
      name: hub.regionName ?? "",
      latitude: null,
      longitude: null,
    })
  }

  if (isLoading) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    )
  }

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Hubs verwalten</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Lege fest, welche Hubs es gibt und zu welcher Region sie gehören.
        </p>
      </div>

      <ProfileSectionCard
        title={editingId != null ? "Hub bearbeiten" : "Hub anlegen"}
        className="overflow-visible"
      >
        <div className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="hub-name">Name</Label>
              <Input
                id="hub-name"
                placeholder="z.B. FHNW"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="hub-type">Typ (optional)</Label>
              <Input
                id="hub-type"
                placeholder="university | incubator | scene"
                value={hubType}
                onChange={(e) => setHubType(e.target.value)}
              />
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="hub-region">Region</Label>
            <RegionInput
              id="hub-region"
              value={region}
              regions={regions ?? []}
              onChange={setRegion}
            />
            <p className="text-xs text-muted-foreground">
              Bestehende Region wählen oder eine neue Gemeinde, einen Bezirk
              oder Kanton suchen.
            </p>
          </div>
          <div className="grid gap-2 sm:flex sm:justify-end">
            {editingId != null && (
              <Button
                className="w-full sm:w-auto"
                variant="ghost"
                size="sm"
                onClick={resetForm}
              >
                <X className="size-3.5" /> Abbrechen
              </Button>
            )}
            <Button
              className="w-full sm:w-auto"
              size="sm"
              onClick={() => void submit()}
              disabled={creating || !name.trim() || !region?.name.trim()}
            >
              <Plus className="size-3.5" />
              {editingId != null ? "Speichern" : "Hub anlegen"}
            </Button>
          </div>
        </div>
      </ProfileSectionCard>

      <ProfileSectionCard title="Bestehende Hubs">
        {byRegion.length === 0 ? (
          <p className="text-sm text-muted-foreground">Noch keine Hubs.</p>
        ) : (
          <div className="space-y-5">
            {byRegion.map(([region, regionHubs]) => (
              <div key={region} className="space-y-2">
                <p className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
                  {region}
                </p>
                {regionHubs.map((hub) => (
                  <div
                    key={hub.id}
                    className="flex flex-wrap items-center gap-2 rounded-lg border bg-card px-3 py-2.5 sm:gap-3 sm:px-4"
                  >
                    <span className="min-w-0 flex-1">
                      <span className="block text-sm font-medium">
                        {hub.name}
                      </span>
                      <span className="block text-xs text-muted-foreground">
                        {hub.slug}
                        {hub.hubType ? ` · ${hub.hubType}` : ""}
                      </span>
                    </span>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => startEdit(hub)}
                    >
                      <Pencil className="size-3.5" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-destructive hover:text-destructive"
                      onClick={() => void deleteHub(hub.id)}
                    >
                      <Trash2 className="size-3.5" />
                    </Button>
                  </div>
                ))}
              </div>
            ))}
          </div>
        )}
      </ProfileSectionCard>
    </div>
  )
}
