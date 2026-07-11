import { Search } from "lucide-react"

import { PlaceholderPage } from "@/components/PlaceholderPage"

/** Demand surface (placeholder): free-text semantic search. */
export function SearchPage() {
  return (
    <PlaceholderPage
      title="Suche"
      subtitle="Freitext-Suche mit semantischem Matching — beschreibe, was du brauchst, und finde passende Profile mit Begründung und zitierten Belegen."
      icon={<Search className="size-6" />}
    />
  )
}
