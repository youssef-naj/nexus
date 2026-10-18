import { useState } from "react"
import { Button } from "@/components/ui/button"
import { useOrg } from "@/features/organizations/orgContext"
import { ApiError } from "@/shared/api/client"
import { TextAreaField } from "@/shared/components/TextAreaField"
import { hasDisallowedControlCharacter } from "@/shared/forms/text"
import { ACTION_SUCCESS, COMMENT_REQUIRED, actionLabel, knownActions } from "./labels"
import { requestErrorMessage } from "./messages"
import { useTransitionRequest } from "./queries"
import type { RequestAction, RequestDetail } from "./schemas"

/** A server-side validation message for the comment, if that is what failed. */
function serverCommentError(error: unknown): string | null {
  if (error instanceof ApiError) {
    const messages = error.problem.errors?.["comment"]
    if (messages && messages.length > 0) return `Comment ${messages.join(" ")}`
  }
  return null
}

/**
 * Submit or review controls. Only the actions the server offered are shown; the server checks
 * every action again. Results are reported to the page (onDone, onFailed) rather than shown here,
 * because after a conflict the request reloads, this panel may disappear, and the explanation must
 * not disappear with it.
 */
export function RequestActionsPanel({
  request,
  onDone,
  onFailed,
}: {
  request: RequestDetail
  onDone: (message: string) => void
  onFailed: (message: string) => void
}) {
  const { organization } = useOrg()
  const transition = useTransitionRequest(organization.id)
  const [comment, setComment] = useState("")
  const [commentError, setCommentError] = useState<string | null>(null)
  const actions = knownActions(request)

  if (actions.length === 0) return null

  const submitting = actions.includes("SUBMIT")
  const mustExplain = actions.some((action) => COMMENT_REQUIRED.has(action))

  async function run(action: RequestAction) {
    setCommentError(null)
    const trimmed = comment.trim()
    if (COMMENT_REQUIRED.has(action) && !trimmed) {
      setCommentError("Enter a comment explaining why.")
      return
    }
    if (trimmed.length > 1000) {
      setCommentError("Comment is too long (maximum 1000 characters).")
      return
    }
    if (hasDisallowedControlCharacter(trimmed)) {
      setCommentError("Comment must not contain control characters.")
      return
    }
    try {
      await transition.mutateAsync({
        requestId: request.id,
        action,
        version: request.version,
        comment: trimmed || undefined,
      })
      setComment("")
      onDone(ACTION_SUCCESS[action])
    } catch (error) {
      const fieldMessage = serverCommentError(error)
      if (fieldMessage) {
        setCommentError(fieldMessage)
      } else {
        onFailed(requestErrorMessage(error))
      }
    }
  }

  return (
    <section aria-labelledby="actions-heading" className="grid gap-3 rounded-lg border p-4">
      <h2 id="actions-heading" className="text-lg font-semibold">
        {submitting ? "Submit" : "Review"}
      </h2>
      <TextAreaField
        id="transition-comment"
        label={mustExplain ? "Comment" : "Comment (optional)"}
        hint={mustExplain ? "Required when rejecting or requesting changes." : undefined}
        error={commentError ? { type: "validate", message: commentError } : undefined}
        value={comment}
        onChange={(event) => setComment(event.target.value)}
      />
      <div className="flex flex-wrap gap-2">
        {actions.map((action) => (
          <Button
            key={action}
            variant={
              action === "REJECT"
                ? "destructive"
                : action === "REQUEST_CHANGES"
                  ? "outline"
                  : "default"
            }
            disabled={transition.isPending}
            onClick={() => void run(action)}
          >
            {actionLabel(action)}
          </Button>
        ))}
      </div>
    </section>
  )
}
