import { AvailabilityEditor } from "@/features/profile/AvailabilityEditor"
import { ProfileSectionCard } from "@/features/profile/ProfileSectionCard"
import { SubscribedTopics } from "@/features/profile/SubscribedTopics"
import type { EntityDto } from "@/types/api"

/**
 * Supply-only onboarding step (design step 4, "Bist du offen für Anfragen?"):
 * the two standing-interest inputs — availability and notification topics.
 * Shown on the completion screen once the entity exists, so it reuses the
 * entity-scoped profile editors verbatim (each owns its own save/feedback).
 */
export function OnboardingAvailabilityStep({ entity }: { entity: EntityDto }) {
  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-xl font-bold tracking-tight">
          Bist du offen für Anfragen?
        </h2>
        <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
          Wenn du offen bist, erscheinst du weiter oben in den Suchergebnissen
          und wirst benachrichtigt, sobald eine passende Anfrage eingeht. Du
          musst nichts aktiv ausschreiben — dein Profil wird gefunden. Du kannst
          das jederzeit ändern.
        </p>
      </div>

      <ProfileSectionCard title="Verfügbarkeit">
        <AvailabilityEditor entity={entity} />
      </ProfileSectionCard>

      <div>
        <h2 className="text-xl font-bold tracking-tight">
          Worüber möchtest du benachrichtigt werden?
        </h2>
        <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
          Abonniere Themen, die dich interessieren. Sobald jemand eine passende
          Anfrage stellt, erhältst du eine Benachrichtigung — auch wenn dein
          Profil nicht direkt gefunden wird. So verpasst du keine relevante
          Gelegenheit in deiner Region.
        </p>
      </div>

      <ProfileSectionCard title="Abonnierte Themen">
        <SubscribedTopics entityId={entity.id} />
      </ProfileSectionCard>
    </div>
  )
}
