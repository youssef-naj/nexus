import { useMemo } from "react"
import { Link, Outlet, useParams } from "react-router"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { ApiError } from "@/shared/api/client"
import { OrgContext, type OrgContextValue } from "./orgContext"
import type { Permission } from "./permissions"
import { useOrganization } from "./queries"

export function OrgLayout() {
  const { orgId = "" } = useParams()
  const { data: organization, error, isPending, refetch } = useOrganization(orgId)

  const value = useMemo<OrgContextValue | null>(
    () =>
      organization
        ? {
            organization,
            can: (permission: Permission) => organization.permissions.includes(permission),
          }
        : null,
    [organization],
  )

  if (isPending) {
    return <Skeleton className="h-8 w-64" aria-label="Loading organization" />
  }

  // The same screen for "does not exist" and "you are not a member" (ADR-0005)
  if (error instanceof ApiError && error.status === 404) {
    return (
      <Alert>
        <AlertTitle>Organization not found</AlertTitle>
        <AlertDescription>
          <p>It may not exist, or you may not have access to it.</p>
          <Link className="mt-2 inline-block underline" to="/">
            Back to your organizations
          </Link>
        </AlertDescription>
      </Alert>
    )
  }

  if (error instanceof ApiError && error.code === "ORGANIZATION_SUSPENDED") {
    return (
      <Alert variant="destructive">
        <AlertTitle>Organization suspended</AlertTitle>
        <AlertDescription>
          <p>This organization is suspended, so its data is unavailable for now.</p>
          <Link className="mt-2 inline-block underline" to="/">
            Back to your organizations
          </Link>
        </AlertDescription>
      </Alert>
    )
  }

  if (error || !value) {
    return (
      <Alert variant="destructive">
        <AlertTitle>Couldn't load the organization</AlertTitle>
        <AlertDescription>
          <p>Check your connection and try again.</p>
          <Button className="mt-3" variant="outline" onClick={() => void refetch()}>
            Try again
          </Button>
        </AlertDescription>
      </Alert>
    )
  }

  return (
    <OrgContext.Provider value={value}>
      <Outlet />
    </OrgContext.Provider>
  )
}
