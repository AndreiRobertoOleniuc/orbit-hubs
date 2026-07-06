import { ZefixFirmSearch } from "@/features/onboarding/ZefixFirmSearch"
import type { FirmDraft } from "@/features/onboarding/types"

interface FirmStepProps {
  firm: FirmDraft | null
  onSelect: (firm: FirmDraft | null) => void
  onConfirm: () => void
}

/** Wizard step (organizations only): pick the firm from the Handelsregister. */
export function FirmStep({ firm, onSelect, onConfirm }: FirmStepProps) {
  return (
    <>
      <h1 className="mb-6 text-3xl font-bold tracking-tight">
        Deine Organisation
      </h1>
      <ZefixFirmSearch firm={firm} onSelect={onSelect} onConfirm={onConfirm} />
    </>
  )
}
