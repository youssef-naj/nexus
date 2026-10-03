import { Navigate, Outlet, useLocation } from "react-router"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { useCurrentUser } from "./queries"

/** Usability only: it decides what to show. The server enforces access on every request. */
export function RequireAuth() {
  const { data: user, isPending, error, refetch } = useCurrentUser()
  const location = useLocation()

  if (isPending) {
    return (
      <main className="grid min-h-screen place-items-center" aria-busy="true">
        <Skeleton className="h-8 w-48" aria-label="Loading" />
      </main>
    )
  }

  if (error) {
    return (
      <main className="mx-auto grid min-h-screen max-w-md place-items-center p-6">
        <Alert variant="destructive">
          <AlertTitle>Can't reach the server</AlertTitle>
          <AlertDescription>
            <p>Check your connection and try again.</p>
            <Button className="mt-3" variant="outline" onClick={() => void refetch()}>
              Try again
            </Button>
          </AlertDescription>
        </Alert>
      </main>
    )
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }

  return <Outlet />
}
