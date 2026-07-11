import { Newspaper } from "lucide-react"

import { PlaceholderPage } from "@/components/PlaceholderPage"

/** Supply surface (placeholder): the public Anfrage-Board. */
export function BoardPage() {
  return (
    <PlaceholderPage
      title="Anfrage-Board"
      subtitle="Öffentlicher Feed offener Anfragen — filterbar nach Tag und Umkreis, sortierbar nach Neueste/Relevanteste und per Tag abonnierbar."
      icon={<Newspaper className="size-6" />}
    />
  )
}
