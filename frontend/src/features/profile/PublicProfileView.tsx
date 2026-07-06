import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { NachweisList } from "@/features/profile/NachweisList"
import { ProfileHeader } from "@/features/profile/ProfileHeader"
import { builtFromLabels } from "@/features/profile/profileUtils"
import type { EntityDto } from "@/types/api"

/**
 * The public/read-only profile: header, Kompetenzen and Nachweise. Used for
 * /profile/:entityId and as the "Öffentlich" preview of the own profile.
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
        {entity.headline ? (
          <p className="text-[15px] leading-relaxed">{entity.headline}</p>
        ) : (
          <p className="text-sm text-muted-foreground">
            Dieses Profil wurde automatisch aus verknüpften Quellen erstellt.
          </p>
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

export function ProfileSectionCard({
  title,
  children,
}: {
  title: string
  children: React.ReactNode
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  )
}
