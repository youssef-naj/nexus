import { Link, useParams } from "react-router"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button, buttonVariants } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { useOrg } from "@/features/organizations/orgContext"
import { ApiError } from "@/shared/api/client"
import { formatDateTime, formatDueDate } from "@/shared/format"
import { categoryLabel } from "./labels"
import { RequestStatusBadge } from "./RequestStatusBadge"
import { useRequest } from "./queries"

export function RequestDetailPage() {
  const { requestId = "" } = useParams()
  const { organization } = useOrg()
  const listPath = `/orgs/${organization.id}/requests`
  const request = useRequest(organization.id, requestId)

  if (request.isPending) {
    return <Skeleton className="h-32 w-full" aria-label="Loading request" />
  }
  // The same screen for "does not exist" and "not yours to see" (ADR-0005)
  if (request.error instanceof ApiError && request.error.status === 404) {
    return (
      <Alert>
        <AlertTitle>Request not found</AlertTitle>
        <AlertDescription>
          <Link className="underline" to={listPath}>
            Back to requests
          </Link>
        </AlertDescription>
      </Alert>
    )
  }
  if (request.error || !request.data) {
    return (
      <Alert variant="destructive">
        <AlertDescription>
          <p>Couldn't load the request.</p>
          <Button className="mt-3" variant="outline" onClick={() => void request.refetch()}>
            Try again
          </Button>
        </AlertDescription>
      </Alert>
    )
  }
  const data = request.data

  return (
    <section className="grid max-w-2xl gap-6">
      <div>
        <Link className="text-sm underline" to={listPath}>
          Back to requests
        </Link>
        <p className="mt-2 text-sm text-muted-foreground">{data.reference}</p>
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold">{data.title}</h1>
          <RequestStatusBadge status={data.status} />
        </div>
      </div>

      {data.editable && (
        <div>
          <Link className={buttonVariants({ variant: "outline" })} to="edit">
            Edit request
          </Link>
        </div>
      )}

      <dl className="grid gap-4 sm:grid-cols-2">
        <Detail label="Category">{categoryLabel(data.category)}</Detail>
        <Detail label="Department">{data.departmentName ?? "—"}</Detail>
        <Detail label="Created by">{data.createdByName}</Detail>
        <Detail label="Assigned to">{data.assigneeName ?? "—"}</Detail>
        <Detail label="Due date">{data.dueDate ? formatDueDate(data.dueDate) : "—"}</Detail>
        <Detail label="Last updated">{formatDateTime(data.updatedAt)}</Detail>
      </dl>

      <div>
        <h2 className="text-sm font-medium text-muted-foreground">Description</h2>
        <p className="mt-1 whitespace-pre-wrap">{data.description || "No description."}</p>
      </div>
    </section>
  )
}

function Detail({ label, children }: { label: string; children: string }) {
  return (
    <div>
      <dt className="text-sm font-medium text-muted-foreground">{label}</dt>
      <dd>{children}</dd>
    </div>
  )
}
