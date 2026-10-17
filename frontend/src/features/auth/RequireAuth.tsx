import { Navigate, Outlet, useLocation } from "react-router"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { ApiError } from "@/shared/api/client"
import { commonErrorMessage } from "@/shared/api/messages"
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

  // Only when we know nothing about the user. A failed background refresh of a session we
  // already have must not replace the page (and discard what the person is typing).
  if (error && user === undefined) {
    const unavailable = error instanceof ApiError && error.status === 503
    return (
      <main className="mx-auto grid min-h-screen max-w-md place-items-center p-6">
        <Alert variant="destructive">
          <AlertTitle>
            {unavailable ? "Temporarily unavailable" : "Can't reach the server"}
          </AlertTitle>
          <AlertDescription>
            <p>{commonErrorMessage(error) ?? "Something unexpected happened. Please try again."}</p>
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
