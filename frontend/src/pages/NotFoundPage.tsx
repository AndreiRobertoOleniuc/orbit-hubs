import { NotFoundState } from "@/components/NotFoundState"

export function NotFoundPage() {
  return (
    <NotFoundState
      title="Seite nicht gefunden"
      description="Diese Seite existiert nicht oder wurde verschoben."
      actionLabel="Zur Startseite"
      actionTo="/"
    />
  )
}
