import { CheckCircle2 } from "lucide-react"
import { useCallback, useEffect, useRef, useState } from "react"
import { Navigate, useNavigate } from "react-router"

import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { EvidenceSelectionStep } from "@/features/onboarding/EvidenceSelectionStep"
import { ExpertiseStep } from "@/features/onboarding/ExpertiseStep"
import { IngestionRunView } from "@/features/onboarding/IngestionRunView"
import { OnboardingProgress } from "@/features/onboarding/OnboardingProgress"
import { firstIncompleteStep } from "@/features/onboarding/onboardingResume"
import { ProfileDetailsStep } from "@/features/onboarding/ProfileDetailsStep"
import { PublishStep } from "@/features/onboarding/PublishStep"
import { FirmStep } from "@/features/onboarding/steps/FirmStep"
import { ModeStep } from "@/features/onboarding/steps/ModeStep"
import { SourcesStep } from "@/features/onboarding/steps/SourcesStep"
import { TypeStep } from "@/features/onboarding/steps/TypeStep"
import type {
  EntityKind,
  FirmDraft,
  OnboardingMode,
} from "@/features/onboarding/types"
import { useIngestionRun } from "@/features/onboarding/useIngestionRun"
import { useAuth } from "@/features/auth/useAuth"
import type { SourceDraft, SourceKind } from "@/features/ingestion/types"
import { useGetMyEntitiesQuery } from "@/lib/api"
import type { EntityDto } from "@/types/api"

const SUPPLY_STEPS = [
  { step: -1, label: "Modus" },
  { step: 0, label: "Profiltyp" },
  { step: 1, label: "Quellen" },
  { step: 2, label: "Nachweise" },
  { step: 3, label: "Profildaten" },
  { step: 4, label: "Kompetenzen" },
  { step: 5, label: "Veröffentlichen" },
]

const DEMAND_STEPS = [
  { step: -1, label: "Modus" },
  { step: 0, label: "Profiltyp" },
  { step: 1, label: "Grundlage" },
  { step: 3, label: "Basisdaten" },
  { step: 5, label: "Abschluss" },
]

const MODE_STEPS = [{ step: -1, label: "Modus" }]

/** Mode-aware onboarding: a compact Demand flow and complete Supply flow. */
export function OnboardingWizard() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const {
    data: myEntities,
    isLoading: entitiesLoading,
    refetch,
  } = useGetMyEntitiesQuery()

  const draftFromApi = myEntities?.find((entity) => entity.status === "draft")
  const activeEntity = myEntities?.find((entity) => entity.status === "active")
  const [entityOverride, setEntityOverride] = useState<EntityDto | null>(null)
  const entity = entityOverride ?? draftFromApi
  const [selectedMode, setSelectedMode] = useState<OnboardingMode | null>(null)
  const mode = entity?.profileMode ?? selectedMode
  const supply = mode === "supply"

  const [kind, setKind] = useState<EntityKind | null>(null)
  const [firm, setFirm] = useState<FirmDraft | null>(null)
  const [sources, setSources] = useState<SourceDraft[]>([])
  const [description, setDescription] = useState("")
  const [stepIndex, setStepIndex] = useState(-1)
  const [finished, setFinished] = useState(false)
  const [draftLoading, setDraftLoading] = useState(false)
  const [draftError, setDraftError] = useState<string | null>(null)
  const initializedDraft = useRef(false)
  const attemptedDraftRefresh = useRef(false)
  const { phase, jobs, run, retry } = useIngestionRun()

  const openDraft = useCallback((draft: EntityDto) => {
    initializedDraft.current = true
    setKind(draft.entityType)
    setEntityOverride(draft)
    setStepIndex(firstIncompleteStep(draft))
  }, [])

  useEffect(() => {
    if (!draftFromApi || initializedDraft.current) return
    openDraft(draftFromApi)
  }, [draftFromApi, openDraft])

  const loadDraft = useCallback(async () => {
    setDraftLoading(true)
    setDraftError(null)
    try {
      const result = await refetch()
      const draft = result.data?.find((item) => item.status === "draft")
      if (!draft) {
        setDraftError(
          "Der Profilentwurf ist noch nicht verfügbar. Versuche fehlgeschlagene Quellen erneut oder klicke nochmals auf Weiter."
        )
        return
      }
      openDraft(draft)
    } catch {
      setDraftError(
        "Der Profilentwurf konnte nicht geladen werden. Bitte versuch es erneut."
      )
    } finally {
      setDraftLoading(false)
    }
  }, [openDraft, refetch])

  useEffect(() => {
    if (
      phase !== "done" ||
      stepIndex !== 1 ||
      draftFromApi ||
      attemptedDraftRefresh.current
    ) {
      return
    }
    attemptedDraftRefresh.current = true
    void loadDraft()
  }, [draftFromApi, loadDraft, phase, stepIndex])

  const isOrg = kind === "organization"
  const currentStep =
    phase === "done" && stepIndex === 1 && draftFromApi
      ? supply
        ? 2
        : 3
      : stepIndex
  const flowSteps =
    mode === "demand"
      ? DEMAND_STEPS
      : mode === "supply"
        ? SUPPLY_STEPS
        : MODE_STEPS

  const addSource = (sourceKind: SourceKind, value: string, file?: File) =>
    setSources((current) => [
      ...current.filter((source) => source.kind !== sourceKind),
      { kind: sourceKind, value, file },
    ])
  const removeSource = (sourceKind: SourceKind) =>
    setSources((current) =>
      current.filter((source) => source.kind !== sourceKind)
    )

  const goBack = () => {
    if (currentStep === -1) {
      navigate("/")
      return
    }
    if (currentStep === 0) {
      if (kind != null) {
        setKind(null)
        setFirm(null)
      } else {
        setSelectedMode(null)
        setStepIndex(-1)
      }
      return
    }
    if (entity && (currentStep === 2 || (!supply && currentStep === 3))) {
      navigate("/")
      return
    }
    const currentFlowIndex = flowSteps.findIndex(
      ({ step }) => step === currentStep
    )
    setStepIndex(flowSteps[Math.max(0, currentFlowIndex - 1)].step)
  }

  const updateAndContinue = (updated: EntityDto, nextStep: number) => {
    setEntityOverride(updated)
    setStepIndex(nextStep)
  }

  if (entitiesLoading) return <OnboardingSkeleton />
  if (finished && entityOverride) {
    return <OnboardingSuccess entity={entityOverride} />
  }
  if (activeEntity && !draftFromApi && phase === "idle") {
    return <Navigate to="/profile" replace />
  }

  const processing =
    phase === "running" || (phase === "done" && currentStep === 1)
  const shownStep = processing ? 1 : currentStep
  const progressIndex = Math.max(
    0,
    flowSteps.findIndex(({ step }) => step === shownStep)
  )

  return (
    <div className="py-6">
      <div className="mx-auto mb-8 grid max-w-5xl grid-cols-[minmax(0,1fr)_minmax(0,max-content)_minmax(0,1fr)] items-center gap-3">
        <Button
          variant="ghost"
          size="sm"
          className="justify-self-start"
          onClick={goBack}
        >
          Zurück
        </Button>
        <div className="max-w-full min-w-0 justify-self-center overflow-x-auto py-1">
          <OnboardingProgress
            current={progressIndex}
            steps={flowSteps.map(({ label }) => label)}
          />
        </div>
        <span className="justify-self-end text-xs text-muted-foreground tabular-nums">
          {progressIndex + 1} / {flowSteps.length}
        </span>
      </div>

      <div className="mx-auto max-w-2xl">
        {processing ? (
          <IngestionRunView
            phase={phase}
            jobs={jobs}
            onRetry={retry}
            onContinue={() => void loadDraft()}
            isContinuing={draftLoading}
            continueError={draftError}
          />
        ) : currentStep === -1 ? (
          <ModeStep
            onSelect={(nextMode) => {
              setSelectedMode(nextMode)
              setStepIndex(0)
            }}
          />
        ) : currentStep === 0 ? (
          kind === "organization" ? (
            <FirmStep
              firm={firm}
              onSelect={setFirm}
              onConfirm={() => setStepIndex(1)}
            />
          ) : (
            <TypeStep
              userLabel={user?.displayName || user?.email}
              onSelect={(selected) => {
                setKind(selected)
                if (selected === "person") setStepIndex(1)
              }}
            />
          )
        ) : currentStep === 1 ? (
          <SourcesStep
            supply={supply}
            isOrg={isOrg}
            firm={firm}
            sources={sources}
            description={description}
            onAddSource={addSource}
            onRemoveSource={removeSource}
            onDescriptionChange={setDescription}
            onFinish={() =>
              void run({
                isOrg,
                firm,
                sources,
                description,
                displayName: isOrg
                  ? firm?.name
                  : user?.displayName || undefined,
                profileMode: mode!,
              })
            }
          />
        ) : supply && entity && currentStep === 2 ? (
          <EvidenceSelectionStep
            entity={entity}
            onContinue={(updated) => updateAndContinue(updated, 3)}
          />
        ) : entity && currentStep === 3 ? (
          <ProfileDetailsStep
            entity={entity}
            onContinue={(updated) => updateAndContinue(updated, supply ? 4 : 5)}
          />
        ) : supply && entity && currentStep === 4 ? (
          <ExpertiseStep
            entity={entity}
            onContinue={(updated) => updateAndContinue(updated, 5)}
          />
        ) : entity ? (
          <PublishStep
            entity={entity}
            onEdit={setStepIndex}
            onPublished={(updated) => {
              setEntityOverride(updated)
              setFinished(true)
            }}
          />
        ) : (
          <IngestionRunView
            phase="done"
            jobs={jobs}
            onRetry={retry}
            onContinue={() => void loadDraft()}
            isContinuing={draftLoading}
            continueError={draftError}
          />
        )}
      </div>
    </div>
  )
}

function OnboardingSkeleton() {
  return (
    <div className="py-6" aria-label="Onboarding wird vorbereitet">
      <div className="mx-auto mb-8 flex max-w-5xl items-center justify-between">
        <Skeleton className="h-8 w-16" />
        <Skeleton className="h-7 w-2/3" />
        <Skeleton className="h-4 w-8" />
      </div>
      <div className="mx-auto max-w-2xl">
        <div className="space-y-3 text-center">
          <Skeleton className="mx-auto h-8 w-72" />
          <Skeleton className="mx-auto h-4 w-4/5" />
        </div>
        <Skeleton className="mt-8 h-72 rounded-xl" />
      </div>
    </div>
  )
}

function OnboardingSuccess({ entity }: { entity: EntityDto }) {
  const navigate = useNavigate()
  const demand = entity.profileMode === "demand"
  return (
    <div className="mx-auto max-w-xl py-16 text-center">
      <CheckCircle2 className="mx-auto size-12 text-green-600" />
      <h1 className="mt-4 text-3xl font-bold tracking-tight">
        {demand ? "Du kannst loslegen." : "Dein Profil ist live."}
      </h1>
      <p className="mx-auto mt-3 max-w-md text-muted-foreground">
        {demand
          ? `${entity.displayName}, dein Absenderprofil ist eingerichtet. Du kannst jetzt suchen oder eine Anfrage ausschreiben.`
          : `${entity.displayName} ist jetzt veröffentlicht. Alle wichtigen Angaben sind eingerichtet und können später im Profil angepasst werden.`}
      </p>
      {demand ? (
        <div className="mt-7 grid gap-3 sm:grid-cols-2">
          <Button onClick={() => navigate("/search")}>Suche starten</Button>
          <Button variant="outline" onClick={() => navigate("/requests/new")}>
            Anfrage ausschreiben
          </Button>
        </div>
      ) : (
        <Button
          className="mt-7 w-full sm:w-auto sm:min-w-52"
          onClick={() => navigate("/profile")}
        >
          Profil ansehen
        </Button>
      )}
    </div>
  )
}
