import { useState } from "react"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { useOrg } from "@/features/organizations/orgContext"
import { ApiError } from "@/shared/api/client"
import { NativeSelect } from "@/shared/components/NativeSelect"
import { requestErrorMessage } from "./messages"
import { useAssignRequest, useReviewers, useUnassignRequest } from "./queries"
import type { RequestDetail } from "./schemas"

function assignmentErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.problem.errors?.["membershipId"]) {
    return "Choose a reviewer who can review this request."
  }
  return requestErrorMessage(error)
}

/**
 * Assign a submitted request to a reviewer. Shown only when the server says this viewer may
 * assign; the server checks again. Assignment is advisory: any reviewer can still decide.
 */
export function AssignmentPanel({
  request,
  onDone,
}: {
  request: RequestDetail
  onDone: (message: string) => void
}) {
  const { organization } = useOrg()
  const reviewers = useReviewers(organization.id, request.assignable)
  const assign = useAssignRequest(organization.id)
  const unassign = useUnassignRequest(organization.id)
  const [selected, setSelected] = useState("")
  const [message, setMessage] = useState<string | null>(null)

  if (!request.assignable) return null

  // The creator cannot review their own request, and re-assigning the same person is a no-op
  const candidates = (reviewers.data ?? []).filter(
    (reviewer) =>
      reviewer.membershipId !== request.createdByMembershipId &&
      reviewer.membershipId !== request.assigneeMembershipId,
  )
  const me = candidates.find((reviewer) => reviewer.you)
  const busy = assign.isPending || unassign.isPending

  async function run(action: () => Promise<unknown>, success: string) {
    setMessage(null)
    try {
      await action()
      setSelected("")
      onDone(success)
    } catch (error) {
      setMessage(assignmentErrorMessage(error))
    }
  }

  function assignTo(membershipId: string, name: string) {
    void run(
      () => assign.mutateAsync({ requestId: request.id, membershipId, version: request.version }),
      `Assigned to ${name}.`,
    )
  }

  return (
    <section aria-labelledby="assignment-heading" className="grid gap-3 rounded-lg border p-4">
      <h2 id="assignment-heading" className="text-lg font-semibold">
        Assignment
      </h2>
      <p className="text-sm text-muted-foreground">
        {request.assigneeName ? `Assigned to ${request.assigneeName}.` : "Not assigned yet."} Any
        reviewer can still decide on this request.
      </p>
      {message && (
        <Alert variant="destructive">
          <AlertDescription>{message}</AlertDescription>
        </Alert>
      )}
      {reviewers.isError && (
        <p className="text-sm text-destructive">Couldn't load the reviewers.</p>
      )}
      <div className="flex flex-wrap items-end gap-2">
        <div className="grid gap-2">
          <Label htmlFor="assign-to">Assign to</Label>
          <NativeSelect
            id="assign-to"
            value={selected}
            onChange={(event) => setSelected(event.target.value)}
          >
            <option value="">Choose a reviewer…</option>
            {candidates.map((reviewer) => (
              <option key={reviewer.membershipId} value={reviewer.membershipId}>
                {reviewer.displayName}
              </option>
            ))}
          </NativeSelect>
        </div>
        <Button
          disabled={!selected || busy}
          onClick={() => {
            const chosen = candidates.find((reviewer) => reviewer.membershipId === selected)
            if (chosen) assignTo(chosen.membershipId, chosen.displayName)
          }}
        >
          Assign
        </Button>
        {me && (
          <Button
            variant="outline"
            disabled={busy}
            onClick={() => assignTo(me.membershipId, "you")}
          >
            Assign to me
          </Button>
        )}
        {request.assigneeMembershipId && (
          <Button
            variant="outline"
            disabled={busy}
            onClick={() =>
              void run(
                () => unassign.mutateAsync({ requestId: request.id, version: request.version }),
                "Assignee removed.",
              )
            }
          >
            Remove assignee
          </Button>
        )}
      </div>
    </section>
  )
}
