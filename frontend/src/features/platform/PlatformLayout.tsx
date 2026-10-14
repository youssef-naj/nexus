import { NavLink, Outlet } from "react-router"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { useCurrentUser } from "@/features/auth/queries"
import { cn } from "@/lib/utils"

function PlatformNavLink({ to, end, children }: { to: string; end?: boolean; children: string }) {
  return (
    <NavLink
      to={to}
      end={end ?? false}
      className={({ isActive }) =>
        cn(
          "-mb-px border-b-2 px-1 py-2 text-sm",
          isActive
            ? "border-primary font-medium"
            : "border-transparent text-muted-foreground hover:text-foreground",
        )
      }
    >
      {children}
    </NavLink>
  )
}

/**
 * Usability only: people without the platform role see the same "not found" as the server gives
 * them, and no platform request is made. The server checks the role on every request.
 */
export function PlatformLayout() {
  const { data: user } = useCurrentUser()

  if (!user?.platformAdmin) {
    return (
      <Alert>
        <AlertTitle>Page not found</AlertTitle>
        <AlertDescription>This page does not exist.</AlertDescription>
      </Alert>
    )
  }

  return (
    <>
      <nav aria-label="Platform" className="mb-4 flex flex-wrap gap-x-4 border-b">
        <PlatformNavLink to="/platform" end>
          Organizations
        </PlatformNavLink>
        <PlatformNavLink to="/platform/audit">Platform audit</PlatformNavLink>
      </nav>
      <Outlet />
    </>
  )
}
