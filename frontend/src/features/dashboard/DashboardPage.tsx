import { Link } from "react-router"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { humanizePermission, roleLabel } from "@/features/organizations/labels"
import { useOrg } from "@/features/organizations/orgContext"
import { actionPastTense, statusLabel } from "@/features/requests/labels"
import { STATUSES } from "@/features/requests/schemas"
import { formatDateTime } from "@/shared/format"
import { useDashboard } from "./queries"
import { countOf, type Dashboard } from "./schemas"

export function DashboardPage() {
  const { organization } = useOrg()
  const dashboard = useDashboard(organization.id)

  return (
    <section className="grid gap-8">
      <div>
        <h1 className="text-2xl font-semibold">{organization.name}</h1>
        <p className="text-sm text-muted-foreground">
          You are signed in here as {roleLabel(organization.role)}.
        </p>
      </div>

      {dashboard.isPending && <Skeleton className="h-40 w-full" aria-label="Loading dashboard" />}

      {dashboard.isError && (
        <Alert variant="destructive">
          <AlertDescription>
            <p>Couldn't load the dashboard.</p>
            <Button className="mt-3" variant="outline" onClick={() => void dashboard.refetch()}>
              Try again
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {dashboard.data && <DashboardContent data={dashboard.data} />}

      <details className="text-sm">
        <summary className="cursor-pointer font-medium">What your role allows</summary>
        <ul className="mt-2 flex flex-wrap gap-2">
          {organization.permissions.map((permission) => (
            <li key={permission}>
              <Badge variant="secondary">{humanizePermission(permission)}</Badge>
            </li>
          ))}
        </ul>
      </details>
    </section>
  )
}

/** A queue card for reviewers: a count that links to the matching filtered list. */
function QueueCard({
  id,
  title,
  count,
  href,
  emptyText,
  suffix,
}: {
  id: string
  title: string
  count: number
  href: string
  emptyText: string
  suffix: string
}) {
  return (
    <section aria-labelledby={id} className="rounded-lg border p-4">
      <h2 id={id} className="text-lg font-semibold">
        {title}
      </h2>
      {count === 0 ? (
        <p className="mt-1 text-sm text-muted-foreground">{emptyText}</p>
      ) : (
        <p className="mt-1">
          <Link className="text-3xl font-semibold underline-offset-4 hover:underline" to={href}>
            {count}
          </Link>{" "}
          <span className="text-sm text-muted-foreground">
            {count === 1 ? "request" : "requests"} {suffix}
          </span>
        </p>
      )}
    </section>
  )
}

function DashboardContent({ data }: { data: Dashboard }) {
  const { organization, can } = useOrg()
  const requestsPath = `/orgs/${organization.id}/requests`
  const organizationWide = data.scope === "ORGANIZATION"
  const awaiting = data.awaitingReview ?? null
  const assigned = data.assignedToMe ?? null

  return (
    <>
      {(assigned !== null || awaiting !== null) && (
        <div className="grid gap-4 sm:grid-cols-2">
          {assigned !== null && (
            <QueueCard
              id="assigned-heading"
              title="Assigned to you"
              count={assigned}
              href={`${requestsPath}?assignedToMe=true`}
              emptyText="Nothing is assigned to you."
              suffix="assigned to you"
            />
          )}
          {awaiting !== null && (
            <QueueCard
              id="awaiting-heading"
              title="Awaiting your review"
              count={awaiting}
              href={`${requestsPath}?reviewable=true`}
              emptyText="Nothing is waiting for your review."
              suffix="submitted by others"
            />
          )}
        </div>
      )}

      <section aria-labelledby="status-heading" className="grid gap-3">
        <h2 id="status-heading" className="text-lg font-semibold">
          {organizationWide ? "Requests" : "Your requests"}
        </h2>
        {data.total === 0 ? (
          <p className="text-sm text-muted-foreground">
            No requests yet.{" "}
            {can("REQUEST_CREATE") && (
              <Link
                className={buttonVariants({ variant: "outline", size: "sm" })}
                to={`${requestsPath}/new`}
              >
                Create a request
              </Link>
            )}
          </p>
        ) : (
          <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {STATUSES.map((status) => (
              <li key={status}>
                <Link
                  to={`${requestsPath}?status=${status}`}
                  className="flex items-center justify-between rounded-lg border p-4 hover:bg-accent focus-visible:outline focus-visible:outline-2"
                >
                  <span>
                    {statusLabel(status)}{" "}
                    <span className="font-semibold">{countOf(data.byStatus, status)}</span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
        <p className="text-sm text-muted-foreground">
          {organizationWide ? `${data.total} in total.` : null}{" "}
          {organizationWide && (
            <Link className="underline" to={`${requestsPath}?mine=true`}>
              You created {data.mine.total} {data.mine.total === 1 ? "request" : "requests"}.
            </Link>
          )}
        </p>
      </section>

      <section aria-labelledby="recent-heading" className="grid gap-3">
        <h2 id="recent-heading" className="text-lg font-semibold">
          Recent activity
        </h2>
        {data.recent.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            Nothing yet. Activity appears when requests are submitted and reviewed.
          </p>
        ) : (
          <ol className="grid gap-3">
            {data.recent.map((event) => (
              <li key={event.id} className="text-sm">
                <span className="font-medium">{event.actorName}</span>{" "}
                {actionPastTense(event.action, event.targetName)}{" "}
                <Link className="underline" to={`${requestsPath}/${event.requestId}`}>
                  {event.reference} · {event.title}
                </Link>
                <span className="block text-xs text-muted-foreground">
                  <time dateTime={event.occurredAt}>{formatDateTime(event.occurredAt)}</time>
                </span>
              </li>
            ))}
          </ol>
        )}
      </section>
    </>
  )
}
