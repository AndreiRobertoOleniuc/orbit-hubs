import { useState } from "react"
import { Loader2, X } from "lucide-react"

import { Button } from "@/components/ui/button"
import { TagAutocompleteInput } from "@/components/TagAutocompleteInput"
import {
  useGetSubscriptionsQuery,
  useUpdateSubscriptionsMutation,
} from "@/lib/api"

/**
 * "Abonnierte Themen" — the tags a user subscribes to so they're notified when
 * a matching Anfrage is distributed, even without a direct skill match (§9).
 */
export function SubscribedTopics({ entityId }: { entityId: number }) {
  const { data: subscriptions, isLoading } = useGetSubscriptionsQuery(entityId)
  const [updateSubscriptions] = useUpdateSubscriptionsMutation()

  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState<string[]>([])

  const startEdit = () => {
    setDraft((subscriptions ?? []).map((s) => s.label))
    setEditing(true)
  }

  const save = async () => {
    await updateSubscriptions({ entityId, tags: draft }).unwrap()
    setEditing(false)
  }

  if (isLoading) {
    return (
      <div className="flex justify-center py-4">
        <Loader2 className="size-5 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (editing) {
    return (
      <div className="space-y-3">
        <div className="flex flex-wrap items-center gap-2">
          {draft.map((topic) => (
            <span
              key={topic}
              className="inline-flex items-center gap-1 rounded-full bg-primary/10 px-3 py-1 text-sm text-primary"
            >
              {topic}
              <button
                onClick={() => setDraft(draft.filter((t) => t !== topic))}
                className="text-primary/60 hover:text-primary"
                aria-label={`Entfernen: ${topic}`}
              >
                <X className="size-3" />
              </button>
            </span>
          ))}
          <TagAutocompleteInput
            selected={draft}
            onAdd={(label) => setDraft([...draft, label])}
            placeholder="Thema abonnieren"
          />
        </div>
        <div className="flex justify-end gap-2">
          <Button size="sm" variant="ghost" onClick={() => setEditing(false)}>
            Abbrechen
          </Button>
          <Button size="sm" onClick={() => void save()}>
            Speichern
          </Button>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-2">
      {subscriptions && subscriptions.length > 0 ? (
        <div className="flex flex-wrap gap-2">
          {subscriptions.map((topic) => (
            <span
              key={topic.id}
              className="inline-flex items-center rounded-full bg-primary/10 px-3 py-1 text-sm text-primary"
            >
              {topic.label}
            </span>
          ))}
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">
          Noch keine Themen abonniert — abonniere Themen, um bei passenden
          Anfragen benachrichtigt zu werden.
        </p>
      )}
      <button
        className="text-xs text-primary hover:underline"
        onClick={startEdit}
      >
        Themen bearbeiten
      </button>
    </div>
  )
}
