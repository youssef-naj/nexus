import { useEffect, useRef } from "react"
import { Outlet, useLocation, useMatches } from "react-router"

function titleOf(matches: ReturnType<typeof useMatches>): string | null {
  for (const match of [...matches].reverse()) {
    const handle = match.handle
    if (typeof handle === "object" && handle !== null && "title" in handle) {
      if (typeof handle.title === "string") return handle.title
    }
  }
  return null
}

export function RootLayout() {
  const location = useLocation()
  const matches = useMatches()
  const previousPath = useRef(location.pathname)
  const title = titleOf(matches)

  useEffect(() => {
    document.title = title ? `${title} · Nexus` : "Nexus"
  }, [title])

  // After navigating to another page, put focus on its main region (not on the first load, and
  // not when only the query string changes)
  useEffect(() => {
    if (previousPath.current !== location.pathname) {
      previousPath.current = location.pathname
      document.getElementById("main")?.focus()
    }
  }, [location.pathname])

  return <Outlet />
}
