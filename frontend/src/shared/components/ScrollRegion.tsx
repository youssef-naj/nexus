import type { ReactNode } from "react"

export function ScrollRegion({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div
      role="region"
      aria-label={label}
      tabIndex={0}
      // The shared Table wraps itself in a scrolling <div>. Switch that off, so this labeled,
      // keyboard-focusable region is the element that actually scrolls.
      className="overflow-x-auto rounded-lg focus-visible:outline focus-visible:outline-2 [&>div]:overflow-visible"
    >
      {children}
    </div>
  )
}
