import { Loader2, Sparkles, X } from "lucide-react"
import { useState } from "react"

import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { TagAutocompleteInput } from "@/components/TagAutocompleteInput"
import {
  useUpdateEntitySummaryMutation,
  useUpdateEntityTagsMutation,
} from "@/lib/api"
import type { EntityDto } from "@/types/api"

export function ExpertiseStep({
  entity,
  onContinue,
}: {
  entity: EntityDto
  onContinue: (entity: EntityDto) => void
}) {
  const [summary, setSummary] = useState(entity.summary ?? "")
  const [tags, setTags] = useState<string[]>(
    entity.tags.map((tag) => tag.label)
  )
  const [error, setError] = useState<string | null>(null)
  const [updateSummary, { isLoading: summarySaving }] =
    useUpdateEntitySummaryMutation()
  const [updateTags, { isLoading: tagsSaving }] = useUpdateEntityTagsMutation()

  const valid = summary.trim().length > 0 && tags.length > 0

  const save = async () => {
    if (!valid) return
    setError(null)
    try {
      await updateSummary({
        entityId: entity.id,
        summary: summary.trim(),
      }).unwrap()
      const updated = await updateTags({
        entityId: entity.id,
        tags,
      }).unwrap()
      onContinue(updated)
    } catch {
      setError(
        "Die Kompetenzen konnten nicht gespeichert werden. Bitte versuch es erneut."
      )
    }
  }

  return (
    <div>
      <div className="text-center">
        <Sparkles className="mx-auto size-9 text-primary" />
        <h1 className="mt-3 text-2xl font-bold tracking-tight">
          Passen diese Kompetenzen zu dir?
        </h1>
        <p className="mx-auto mt-2 max-w-lg text-sm leading-relaxed text-muted-foreground">
          Orbit hat den Vorschlag nur aus deinen ausgewählten Nachweisen
          erstellt. Prüfe und bearbeite ihn vor der Veröffentlichung.
        </p>
      </div>

      <div className="mt-6 space-y-5 rounded-xl border bg-card p-5">
        <div className="space-y-2">
          <div className="flex items-center justify-between gap-3">
            <label htmlFor="onboarding-summary" className="text-sm font-medium">
              Profilbeschreibung <span className="text-destructive">*</span>
            </label>
            <span className="text-xs text-muted-foreground">
              Von Orbit vorgeschlagen – bitte prüfen
            </span>
          </div>
          <Textarea
            id="onboarding-summary"
            rows={6}
            value={summary}
            placeholder="Beschreibe deine Erfahrung und Kompetenzen in einigen Sätzen."
            className="leading-relaxed"
            onChange={(event) => setSummary(event.target.value)}
          />
        </div>

        <div className="space-y-2">
          <p className="text-sm font-medium">
            Kompetenzen <span className="text-destructive">*</span>
          </p>
          <div className="flex flex-wrap items-center gap-2">
            {tags.map((tag) => (
              <span
                key={tag}
                className="inline-flex items-center gap-1 rounded-full bg-primary/10 px-3 py-1 text-sm text-primary"
              >
                {tag}
                <button
                  type="button"
                  aria-label={`${tag} entfernen`}
                  onClick={() =>
                    setTags((current) =>
                      current.filter((value) => value !== tag)
                    )
                  }
                >
                  <X className="size-3" />
                </button>
              </span>
            ))}
            <TagAutocompleteInput
              selected={tags}
              onAdd={(tag) => setTags((current) => [...current, tag])}
              placeholder="Kompetenz hinzufügen"
            />
          </div>
        </div>
      </div>

      {!valid && (
        <p className="mt-3 text-sm text-muted-foreground">
          Eine Beschreibung und mindestens eine Kompetenz sind erforderlich.
        </p>
      )}
      {error && (
        <p className="mt-3 text-sm text-destructive" role="alert">
          {error}
        </p>
      )}
      <Button
        className="mt-6 w-full"
        disabled={!valid || summarySaving || tagsSaving}
        onClick={() => void save()}
      >
        {(summarySaving || tagsSaving) && (
          <Loader2 className="size-4 animate-spin" />
        )}
        Kompetenzen speichern und weiter
      </Button>
    </div>
  )
}
