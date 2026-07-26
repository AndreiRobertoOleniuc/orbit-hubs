import { Link } from "react-router"
import { SearchX } from "lucide-react"

import { Button } from "@/components/ui/button"

interface NotFoundStateProps {
  title: string
  description: string
  actionLabel: string
  actionTo: string
}

/**
 * The one not-found treatment — used by the 404 route and by resource pages
 * (profile, Anfrage) whose id doesn't resolve, so "doesn't exist" always looks
 * the same regardless of how the user got there.
 */
export function NotFoundState({
  title,
  description,
  actionLabel,
  actionTo,
}: NotFoundStateProps) {
  return (
    <div className="mx-auto max-w-xl py-16 text-center">
      <div className="mx-auto flex size-12 items-center justify-center rounded-full bg-muted">
        <SearchX className="size-6 text-muted-foreground" />
      </div>
      <h1 className="mt-5 text-2xl font-semibold tracking-tight">{title}</h1>
      <p className="mx-auto mt-2 max-w-md text-muted-foreground">
        {description}
      </p>
      <Button asChild variant="outline" className="mt-6">
        <Link to={actionTo}>{actionLabel}</Link>
      </Button>
    </div>
  )
}
