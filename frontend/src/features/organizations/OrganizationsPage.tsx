import { Link, Navigate } from "react-router"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { roleLabel } from "./labels"
import { useMyOrganizations } from "./queries"

export function OrganizationsPage() {
  const { data: organizations, error, isPending, refetch } = useMyOrganizations()

  if (isPending) {
    return (
      <div className="grid gap-3" aria-busy="true">
        <Skeleton className="h-8 w-56" aria-label="Loading organizations" />
        <Skeleton className="h-16 w-full" />
      </div>
    )
  }

  if (error) {
    return (
      <Alert variant="destructive">
        <AlertTitle>Couldn't load your organizations</AlertTitle>
        <AlertDescription>
          <p>Check your connection and try again.</p>
          <Button className="mt-3" variant="outline" onClick={() => void refetch()}>
            Try again
          </Button>
        </AlertDescription>
      </Alert>
    )
  }

  const only = organizations.length === 1 ? organizations[0] : undefined
  if (only) {
    return <Navigate to={`/orgs/${only.id}`} replace />
  }

  if (organizations.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>
            <h1 className="text-2xl">Welcome to Nexus</h1>
          </CardTitle>
          <CardDescription>
            You don't belong to any organization yet. Create one to get started, or ask an
            administrator to invite you.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Link className={buttonVariants()} to="/organizations/new">
            Create an organization
          </Link>
        </CardContent>
      </Card>
    )
  }

  return (
    <section className="grid gap-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Your organizations</h1>
        <Link className={buttonVariants({ variant: "outline" })} to="/organizations/new">
          Create organization
        </Link>
      </div>
      <ul className="grid gap-3">
        {organizations.map((organization) => (
          <li key={organization.id}>
            <Link
              to={`/orgs/${organization.id}`}
              className="flex items-center justify-between rounded-lg border p-4 hover:bg-accent focus-visible:outline focus-visible:outline-2"
            >
              <span className="font-medium">{organization.name}</span>
              <span className="flex items-center gap-2">
                {organization.status === "SUSPENDED" && (
                  <Badge variant="destructive">Suspended</Badge>
                )}
                <Badge variant="secondary">{roleLabel(organization.role)}</Badge>
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
