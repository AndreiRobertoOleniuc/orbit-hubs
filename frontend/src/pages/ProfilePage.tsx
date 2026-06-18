import { useParams } from "react-router-dom"

import { Button } from "@/components/ui/button"
import { useGetMeQuery, useLazyGetMeQuery } from "@/lib/api"

export function ProfilePage() {
  const { entityId } = useParams<{ entityId?: string }>()

  const {
    data: autoMe,
    isLoading: autoIsLoading,
    error: autoError,
  } = useGetMeQuery(undefined, {
    skip: Boolean(entityId),
  })

  const [triggerLazyMe, lazyResult] = useLazyGetMeQuery()

  if (entityId) {
    return (
      <div className="space-y-2">
        <h1 className="text-2xl font-semibold">Profil</h1>
        <p className="text-muted-foreground">
          Profil-ID: <code className="rounded bg-muted px-1">{entityId}</code>
          . Hier erscheint die öffentliche Profilansicht.
        </p>
      </div>
    )
  }

  return (
    <div className="space-y-8">
      <h1 className="text-2xl font-semibold">Eigenes Profil</h1>

      <section className="space-y-3 rounded-lg border p-4">
        <h2 className="font-medium">Automatisch bei Mount (useGetMeQuery)</h2>

        {autoIsLoading && (
          <p className="text-sm text-muted-foreground">Lade /me …</p>
        )}

        {autoError && (
          <div className="rounded-md border border-destructive/50 bg-destructive/10 p-3 text-sm text-destructive">
            <p className="font-medium">/me Fehler</p>
            <p>
              {autoError && "status" in autoError
                ? `HTTP ${autoError.status}: ${JSON.stringify(autoError.data)}`
                : "Netzwerkfehler"}
            </p>
          </div>
        )}

        {autoMe && (
          <dl className="grid max-w-md grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
            <dt className="font-medium text-muted-foreground">Backend userId</dt>
            <dd>
              <code className="rounded bg-muted px-1">{autoMe.userId}</code>
            </dd>

            <dt className="font-medium text-muted-foreground">Backend email</dt>
            <dd>{autoMe.email}</dd>
          </dl>
        )}

        {!autoIsLoading && !autoError && !autoMe && (
          <p className="text-sm text-muted-foreground">
            Keine /me-Daten erhalten.
          </p>
        )}
      </section>

      <section className="space-y-3 rounded-lg border p-4">
        <h2 className="font-medium">Per Button-Klick (useLazyGetMeQuery)</h2>

        <Button
          onClick={() => triggerLazyMe()}
          disabled={lazyResult.isLoading}
        >
          {lazyResult.isLoading ? "Lade …" : "/me aufrufen"}
        </Button>

        {lazyResult.isLoading && (
          <p className="text-sm text-muted-foreground">Lade /me …</p>
        )}

        {lazyResult.error && (
          <div className="rounded-md border border-destructive/50 bg-destructive/10 p-3 text-sm text-destructive">
            <p className="font-medium">/me Fehler</p>
            <p>
              {lazyResult.error && "status" in lazyResult.error
                ? `HTTP ${lazyResult.error.status}: ${JSON.stringify(lazyResult.error.data)}`
                : "Netzwerkfehler"}
            </p>
          </div>
        )}

        {lazyResult.data && (
          <dl className="grid max-w-md grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
            <dt className="font-medium text-muted-foreground">Backend userId</dt>
            <dd>
              <code className="rounded bg-muted px-1">{lazyResult.data.userId}</code>
            </dd>

            <dt className="font-medium text-muted-foreground">Backend email</dt>
            <dd>{lazyResult.data.email}</dd>
          </dl>
        )}

        {!lazyResult.isLoading && !lazyResult.error && !lazyResult.data && (
          <p className="text-sm text-muted-foreground">
            Noch nicht manuell aufgerufen.
          </p>
        )}
      </section>
    </div>
  )
}
