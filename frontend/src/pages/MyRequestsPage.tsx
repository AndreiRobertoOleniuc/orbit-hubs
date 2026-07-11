import { ClipboardList } from "lucide-react"

import { PlaceholderPage } from "@/components/PlaceholderPage"

/** Demand surface (placeholder): the user's own posted Anfragen + lifecycle. */
export function MyRequestsPage() {
  return (
    <PlaceholderPage
      title="Meine Anfragen"
      subtitle="Deine ausgeschriebenen Anfragen und ihr Lebenszyklus: Offen → Verteilt → Kontaktiert → Geschlossen, mit Status und Rückmeldungen."
      icon={<ClipboardList className="size-6" />}
    />
  )
}
