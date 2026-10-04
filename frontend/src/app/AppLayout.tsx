import { Link, Outlet } from "react-router"
import { OrgSwitcher } from "@/app/OrgSwitcher"
import { Button } from "@/components/ui/button"
import { useCurrentUser, useLogout } from "@/features/auth/queries"

export function AppLayout() {
  const { data: user } = useCurrentUser()
  const logout = useLogout()

  return (
    <div className="min-h-screen">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:rounded focus:bg-background focus:p-2"
      >
        Skip to content
      </a>
      <header className="border-b">
        <div className="mx-auto flex max-w-5xl items-center justify-between p-4">
          <div className="flex items-center gap-3">
            <Link to="/" className="text-lg font-semibold">
              Nexus
            </Link>
            <OrgSwitcher />
          </div>
          <div className="flex items-center gap-3">
            {user && <span className="text-sm text-muted-foreground">{user.displayName}</span>}
            <Button
              variant="outline"
              size="sm"
              disabled={logout.isPending}
              onClick={() => logout.mutate()}
            >
              Sign out
            </Button>
          </div>
        </div>
      </header>
      <main id="main" className="mx-auto max-w-5xl p-4">
        <Outlet />
      </main>
    </div>
  )
}
