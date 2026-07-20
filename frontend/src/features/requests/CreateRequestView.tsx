import { useState } from "react"
import { useLocation, useNavigate } from "react-router-dom"
import { ArrowLeft, Loader2, X } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { TagAutocompleteInput } from "@/components/TagAutocompleteInput"
import { HubRegionSelect } from "@/features/search/HubRegionSelect"
import { SCOPE_ALL, decodeScope } from "@/features/search/scopeUtils"
import {
  URGENCY_LABEL,
  URGENCY_STRATEGY,
} from "@/features/requests/requestUtils"
import { useCreateRequestMutation } from "@/lib/api"
import { cn, todayIso } from "@/lib/utils"
import type { RequestUrgency } from "@/types/api"

const FIELD =
  "h-9 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"

const CAP =
  "text-[11px] font-semibold tracking-wide text-muted-foreground uppercase"

/** What the search hands over when no match was found ("Anfrage erstellen"). */
export interface CreateRequestPrefill {
  queryText?: string
  scope?: string
  wantFrom?: string
  wantTo?: string
  wantPensum?: string
}

const URGENCIES: RequestUrgency[] = ["flexible", "soon", "urgent"]

/**
 * The "Anfrage ausschreiben" form — turns a (failed) search into a persistent
 * board request. Prefilled from the search via router state; urgency shows the
 * distribution strategy transparently ("So verteilen wir deine Anfrage", §8.3).
 */
export function CreateRequestView() {
  const navigate = useNavigate()
  const location = useLocation()
  const prefill = (location.state ?? {}) as CreateRequestPrefill

  const [queryText, setQueryText] = useState(prefill.queryText ?? "")
  const [title, setTitle] = useState("")
  const [description, setDescription] = useState("")
  const [scope, setScope] = useState(prefill.scope ?? SCOPE_ALL)
  const [urgency, setUrgency] = useState<RequestUrgency>("flexible")
  const [deadline, setDeadline] = useState("")
  const [wantFrom, setWantFrom] = useState(prefill.wantFrom ?? "")
  const [wantTo, setWantTo] = useState(prefill.wantTo ?? "")
  const [wantPensum, setWantPensum] = useState(prefill.wantPensum ?? "")
  const [tags, setTags] = useState<string[]>([])

  const [createRequest, { isLoading, isError }] = useCreateRequestMutation()

  const submit = async () => {
    if (!queryText.trim()) return
    const created = await createRequest({
      title: title.trim() || null,
      description: description.trim() || null,
      queryText: queryText.trim(),
      scope: decodeScope(scope),
      urgency,
      deadline: deadline || null,
      wantFrom: wantFrom || null,
      wantTo: wantTo || null,
      wantPensumPct: wantPensum ? Number(wantPensum) : null,
      tags,
    }).unwrap()
    navigate(`/board/${created.id}`, { replace: true })
  }

  return (
    <div className="mx-auto max-w-2xl py-6">
      <button
        type="button"
        onClick={() => navigate(-1)}
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground transition-colors hover:text-foreground"
      >
        <ArrowLeft className="size-4" /> Zurück
      </button>

      <h1 className="mt-4 text-2xl font-bold tracking-tight">
        Anfrage ausschreiben
      </h1>
      <p className="mt-1.5 max-w-xl text-[15px] leading-relaxed text-muted-foreground">
        Deine Anfrage erscheint öffentlich auf dem Anfrage-Board. Passende
        Profile werden benachrichtigt und können sich direkt bei dir melden.
      </p>

      <p className="mt-4 text-xs text-muted-foreground">
        <span className="text-destructive">*</span> Pflichtfeld
      </p>

      <div className="mt-4 flex flex-col gap-6">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="req-query" className={CAP}>
            Was suchst du? <span className="text-destructive">*</span>
          </label>
          <Textarea
            id="req-query"
            rows={3}
            autoFocus={!prefill.queryText}
            className="text-base"
            placeholder="Beschreibe dein Anliegen in eigenen Worten, z. B. «Embedded-Firmware-Entwickler für 2 Monate, STM32 + FreeRTOS»"
            value={queryText}
            onChange={(e) => setQueryText(e.target.value)}
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="req-title" className={CAP}>
            Titel (optional)
          </label>
          <Input
            id="req-title"
            placeholder="Kurzer Titel für das Board, z. B. «Embedded-Firmware-Entwickler für 2 Monate»"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="req-desc" className={CAP}>
            Beschreibung (optional)
          </label>
          <Textarea
            id="req-desc"
            rows={4}
            placeholder="Kontext für Interessierte: Was ist die Aufgabe, was bringst du mit, wie arbeitet ihr zusammen?"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </div>

        <div className="flex flex-wrap items-end gap-4">
          <div className="flex min-w-52 flex-1 flex-col gap-1.5">
            <span className={CAP}>Hub / Region</span>
            <HubRegionSelect value={scope} onChange={setScope} />
          </div>
          <div className="flex flex-col gap-1.5">
            <span className={CAP}>Zeitraum (optional)</span>
            <div className="flex items-center gap-2">
              <input
                type="date"
                aria-label="Von"
                className={FIELD}
                min={todayIso()}
                max={wantTo || undefined}
                value={wantFrom}
                onChange={(e) => setWantFrom(e.target.value)}
              />
              <span aria-hidden="true" className="text-muted-foreground">
                –
              </span>
              <input
                type="date"
                aria-label="Bis"
                className={FIELD}
                min={wantFrom || todayIso()}
                value={wantTo}
                onChange={(e) => setWantTo(e.target.value)}
              />
            </div>
          </div>
          <div className="flex flex-col gap-1.5">
            <span className={CAP}>Umfang ca. %</span>
            <input
              type="number"
              min={0}
              max={100}
              step={10}
              inputMode="numeric"
              aria-label="Umfang in Prozent"
              placeholder="z. B. 50"
              className={cn(FIELD, "w-24")}
              value={wantPensum}
              onChange={(e) => setWantPensum(e.target.value)}
            />
          </div>
        </div>

        <div className="flex flex-col gap-1.5">
          <span className={CAP}>Dringlichkeit</span>
          <div
            role="radiogroup"
            aria-label="Dringlichkeit"
            className="grid gap-2 sm:grid-cols-3"
          >
            {URGENCIES.map((u) => {
              const on = urgency === u
              return (
                <button
                  key={u}
                  type="button"
                  role="radio"
                  aria-checked={on}
                  onClick={() => setUrgency(u)}
                  className={cn(
                    "rounded-lg border px-3.5 py-2.5 text-left text-sm font-medium transition-colors",
                    on
                      ? "border-primary bg-primary/5 text-foreground"
                      : "text-muted-foreground hover:border-foreground/30 hover:text-foreground"
                  )}
                >
                  {URGENCY_LABEL[u]}
                </button>
              )
            })}
          </div>
          <p className="mt-1 text-[12.5px] leading-relaxed text-muted-foreground">
            <strong className="text-foreground/80">
              So verteilen wir deine Anfrage:
            </strong>{" "}
            {URGENCY_STRATEGY[urgency]}
          </p>
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="req-deadline" className={CAP}>
            Antworten bis (optional)
          </label>
          <input
            id="req-deadline"
            type="date"
            className={cn(FIELD, "w-fit")}
            min={todayIso()}
            value={deadline}
            onChange={(e) => setDeadline(e.target.value)}
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <span className={CAP}>Themen-Tags</span>
          <div className="flex flex-wrap items-center gap-2">
            {tags.map((t) => (
              <span
                key={t}
                className="inline-flex items-center gap-1 rounded-full bg-primary/10 py-1 pr-1.5 pl-3 text-sm text-primary"
              >
                {t}
                <button
                  type="button"
                  aria-label={`Tag ${t} entfernen`}
                  onClick={() => setTags(tags.filter((x) => x !== t))}
                  className="rounded-full p-0.5 transition-colors hover:bg-primary/15"
                >
                  <X className="size-3" />
                </button>
              </span>
            ))}
            <TagAutocompleteInput
              selected={tags}
              onAdd={(label) => setTags([...tags, label])}
              placeholder="Tag hinzufügen…"
            />
          </div>
          <p className="text-[12.5px] text-muted-foreground">
            Tags steuern, wer benachrichtigt wird: Profile mit diesen
            Kompetenzen und Personen, die das Thema abonniert haben.
          </p>
        </div>

        {isError && (
          <p className="text-sm text-red-600 dark:text-red-400">
            Die Anfrage konnte nicht erstellt werden. Bitte versuche es erneut.
          </p>
        )}

        <Button
          className="w-full"
          disabled={!queryText.trim() || isLoading}
          onClick={submit}
        >
          {isLoading && <Loader2 className="size-4 animate-spin" />}
          Anfrage veröffentlichen
        </Button>
        <p className="-mt-3 text-center text-xs text-muted-foreground">
          Öffentlich auf dem Board sichtbar. Du kannst die Anfrage jederzeit
          schliessen.
        </p>
      </div>
    </div>
  )
}
