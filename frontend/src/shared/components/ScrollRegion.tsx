import type { ReactNode } from "react"

export function ScrollRegion({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div
      role="region"
      aria-label={label}
      tabIndex={0}
      className="overflow-x-auto rounded-lg focus-visible:outline focus-visible:outline-2"
    >
      {children}
    </div>
  )
}
