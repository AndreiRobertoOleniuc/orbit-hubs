import { Inbox } from "lucide-react"

import { PlaceholderPage } from "@/components/PlaceholderPage"

/** Supply surface (placeholder): incoming contact requests + matching Anfragen. */
export function InboxPage() {
  return (
    <PlaceholderPage
      title="Postfach"
      subtitle="Eingehende Kontaktanfragen zu deinem Profil und offene passende Anfragen, die du direkt beantworten kannst."
      icon={<Inbox className="size-6" />}
    />
  )
}
