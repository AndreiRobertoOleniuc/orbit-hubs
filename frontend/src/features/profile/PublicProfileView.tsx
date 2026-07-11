import { Card, CardContent } from "@/components/ui/card"
import { NachweisList } from "@/features/profile/NachweisList"
import { ProfileHeader } from "@/features/profile/ProfileHeader"
import { builtFromLabels } from "@/features/profile/profileUtils"
import type { EntityDto } from "@/types/api"

/**
 * The public/read-only profile: header, Kompetenzen (summary + tags), and
 * Nachweise. Used for /profile/:entityId and as the "Öffentlich" preview of
 * the own profile.
 */
export function PublicProfileView({ entity }: { entity: EntityDto }) {
  const builtFrom = builtFromLabels(entity)
  return (
    <div className="space-y-8">
      <ProfileHeader entity={entity} showContact />

      <section className="space-y-3">
        <h3 className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
          Kompetenzen
        </h3>
        {entity.summary ? (
          <p className="text-[15px] leading-relaxed">{entity.summary}</p>
        ) : entity.headline ? (
          <p className="text-[15px] leading-relaxed">{entity.headline}</p>
        ) : (
          <p className="text-sm text-muted-foreground">
            Dieses Profil wurde automatisch aus verknüpften Quellen erstellt.
          </p>
        )}
        {entity.tags.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {entity.tags.map((tag) => (
              <span
                key={tag.id}
                className="inline-flex items-center rounded-full bg-primary/10 px-3 py-1 text-sm text-primary"
              >
                {tag.label}
              </span>
            ))}
          </div>
        )}
        {entity.hubs.length > 0 && (
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-xs text-muted-foreground">Hubs:</span>
            {entity.hubs.map((hub) => (
              <span
                key={hub.id}
                className="inline-flex items-center rounded-full border px-3 py-1 text-sm text-muted-foreground"
              >
                {hub.name}
              </span>
            ))}
          </div>
        )}
        <p className="text-sm text-muted-foreground">
          Erstellt aus:{" "}
          {builtFrom.length > 0 ? builtFrom.join(", ") : "manueller Eingabe"}
        </p>
      </section>

      <section className="space-y-3">
        <h3 className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
          Nachweise
        </h3>
        <Card>
          <CardContent>
            <NachweisList sources={entity.sources} />
          </CardContent>
        </Card>
      </section>
    </div>
  )
}
