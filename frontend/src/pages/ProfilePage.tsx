import { useParams } from "react-router-dom"

export function ProfilePage() {
  const { entityId } = useParams<{ entityId?: string }>()

  return (
    <div className="space-y-2">
      <h1 className="text-2xl font-semibold">Profil</h1>
      <p className="text-muted-foreground">
        {entityId ? (
          <>
            Profil-ID: <code className="rounded bg-muted px-1">{entityId}</code>
            . Hier erscheint die öffentliche Profilansicht.
          </>
        ) : (
          "Hier erscheint das eigene Profil."
        )}
      </p>
    </div>
  )
}
