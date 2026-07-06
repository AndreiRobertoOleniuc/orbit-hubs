import { Building2, User } from "lucide-react"

import type { EntityKind } from "@/features/onboarding/types"

interface TypeStepProps {
  userLabel?: string
  onSelect: (kind: EntityKind) => void
}

/** Wizard step 1: "Wer bist du?" — Person or Organisation. */
export function TypeStep({ userLabel, onSelect }: TypeStepProps) {
  return (
    <>
      <h1 className="mb-8 text-center text-3xl font-bold tracking-tight">
        Wer bist du?
      </h1>
      <div className="grid gap-4 sm:grid-cols-2">
        <button
          type="button"
          className="flex flex-col items-center gap-3 rounded-xl border bg-card p-8 transition-colors hover:border-primary/50 hover:bg-accent"
          onClick={() => onSelect("person")}
        >
          <User className="size-9 text-primary" />
          <span className="text-lg font-semibold">Person</span>
        </button>
        <button
          type="button"
          className="flex flex-col items-center gap-3 rounded-xl border bg-card p-8 transition-colors hover:border-primary/50 hover:bg-accent"
          onClick={() => onSelect("organization")}
        >
          <Building2 className="size-9 text-primary" />
          <span className="text-lg font-semibold">Organisation</span>
        </button>
      </div>
      {userLabel && (
        <p className="mt-8 text-center text-sm text-muted-foreground">
          Angemeldet als {userLabel} — dein Profil wird mit diesem Konto
          verknüpft.
        </p>
      )}
    </>
  )
}
