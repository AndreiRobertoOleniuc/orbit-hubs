import { cn } from "@/lib/utils"

/** The Orbit mark from the design prototype: planet, tilted ring, accent moon. */
export function OrbitLogo({ className }: { className?: string }) {
  return (
    <span
      className={cn(
        "flex items-center gap-2 text-base font-semibold tracking-tight",
        className
      )}
    >
      <svg
        width="26"
        height="26"
        viewBox="0 0 22 22"
        fill="none"
        aria-hidden="true"
      >
        <circle
          cx="11"
          cy="11"
          r="4.2"
          stroke="currentColor"
          strokeWidth="1.8"
        />
        <ellipse
          cx="11"
          cy="11"
          rx="9.2"
          ry="3.6"
          stroke="currentColor"
          strokeWidth="1.3"
          transform="rotate(-24 11 11)"
          opacity="0.45"
        />
        <circle cx="18.6" cy="6.4" r="1.9" className="fill-primary" />
      </svg>
      Orbit
    </span>
  )
}
