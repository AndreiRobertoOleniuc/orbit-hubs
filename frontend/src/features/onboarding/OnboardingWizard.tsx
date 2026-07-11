import { useMemo, useState } from "react"
import { Navigate, useNavigate, useSearchParams } from "react-router-dom"
import { Loader2 } from "lucide-react"

import { Button } from "@/components/ui/button"
import { WizardDots } from "@/components/WizardDots"
import type { SourceDraft, SourceKind } from "@/features/ingestion/types"
import { IngestionRunView } from "@/features/onboarding/IngestionRunView"
import { FirmStep } from "@/features/onboarding/steps/FirmStep"
import { SourcesStep } from "@/features/onboarding/steps/SourcesStep"
import { TypeStep } from "@/features/onboarding/steps/TypeStep"
import type {
  EntityKind,
  FirmDraft,
  OnboardingMode,
} from "@/features/onboarding/types"
import { useIngestionRun } from "@/features/onboarding/useIngestionRun"
import { useAuth } from "@/features/auth/useAuth"
import { useGetMyEntitiesQuery } from "@/lib/api"

type WizardStep = "type" | "firm" | "sources"

/**
 * The onboarding state machine. The mode ("supply" | "demand") comes from the
 * start page via ?mode=; steps are type → firm (organizations only) → sources,
 * then the wizard hands off to the ingestion run.
 */
export function OnboardingWizard() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [searchParams] = useSearchParams()
  const mode = searchParams.get("mode") as OnboardingMode | null
  const supply = mode !== "demand"

  const [kind, setKind] = useState<EntityKind | null>(null)
  const [firm, setFirm] = useState<FirmDraft | null>(null)
  const [sources, setSources] = useState<SourceDraft[]>([])
  const [description, setDescription] = useState("")
  const [stepIndex, setStepIndex] = useState(0)
  const { phase, jobs, run, retry } = useIngestionRun()
  const { data: myEntities, isLoading: entitiesLoading } =
    useGetMyEntitiesQuery()

  const isOrg = kind === "organization"
  const steps = useMemo<WizardStep[]>(
    () => (isOrg ? ["type", "firm", "sources"] : ["type", "sources"]),
    [isOrg]
  )
  const step = steps[stepIndex]

  if (!mode) {
    return <Navigate to="/" replace />
  }

  if (phase !== "idle") {
    return (
      <IngestionRunView
        phase={phase}
        jobs={jobs}
        supply={supply}
        onRetry={retry}
      />
    )
  }

  // Onboarding is a one-time flow. Guarded only while idle: during the run the
  // freshly created entity would otherwise redirect away from the progress view.
  if (entitiesLoading) {
    return (
      <div className="flex justify-center py-24">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    )
  }
  if (myEntities && myEntities.length > 0) {
    return <Navigate to="/profile" replace />
  }

  const addSource = (sourceKind: SourceKind, value: string, file?: File) =>
    setSources((prev) => [
      ...prev.filter((s) => s.kind !== sourceKind),
      { kind: sourceKind, value, file },
    ])
  const removeSource = (sourceKind: SourceKind) =>
    setSources((prev) => prev.filter((s) => s.kind !== sourceKind))

  const goBack = () => {
    if (stepIndex === 0) navigate("/")
    else setStepIndex(stepIndex - 1)
  }

  return (
    <div className="mx-auto max-w-xl py-6">
      <div className="mb-8 grid grid-cols-[1fr_auto_1fr] items-center">
        <Button
          variant="ghost"
          size="sm"
          className="justify-self-start"
          onClick={goBack}
        >
          Zurück
        </Button>
        <WizardDots total={steps.length} current={stepIndex} />
        <span />
      </div>

      {step === "type" && (
        <TypeStep
          userLabel={user?.displayName || user?.email}
          onSelect={(selected) => {
            setKind(selected)
            setStepIndex(1)
          }}
        />
      )}

      {step === "firm" && (
        <FirmStep
          firm={firm}
          onSelect={setFirm}
          onConfirm={() => setStepIndex(stepIndex + 1)}
        />
      )}

      {step === "sources" && (
        <SourcesStep
          supply={supply}
          isOrg={isOrg}
          firm={firm}
          sources={sources}
          description={description}
          onAddSource={addSource}
          onRemoveSource={removeSource}
          onDescriptionChange={setDescription}
          onFinish={() => void run({ isOrg, firm, sources, description })}
        />
      )}
    </div>
  )
}
