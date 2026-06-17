import { Link } from "react-router-dom"

import { Button } from "@/components/ui/button"

export function NotFoundPage() {
  return (
    <div className="space-y-4 py-12 text-center">
      <h1 className="text-4xl font-semibold">404</h1>
      <p className="text-muted-foreground">
        Diese Seite konnte nicht gefunden werden.
      </p>
      <Button asChild>
        <Link to="/search">Zurück zur Suche</Link>
      </Button>
    </div>
  )
}
