import {
  actionSchema,
  type RequestAction,
  type RequestCategory,
  type RequestDetail,
  type RequestStatus,
} from "./schemas"

const CATEGORY_LABELS: Record<RequestCategory, string> = {
  IT_SUPPORT: "IT support",
  FACILITIES: "Facilities",
  HR: "HR",
  FINANCE: "Finance",
  OTHER: "Other",
}

const STATUS_LABELS: Record<RequestStatus, string> = {
  DRAFT: "Draft",
  SUBMITTED: "Submitted",
  CHANGES_REQUESTED: "Changes requested",
  APPROVED: "Approved",
  REJECTED: "Rejected",
}

export const categoryLabel = (category: RequestCategory) => CATEGORY_LABELS[category]
export const statusLabel = (status: RequestStatus) => STATUS_LABELS[status]

export function statusVariant(status: RequestStatus): "default" | "secondary" | "destructive" {
  switch (status) {
    case "REJECTED":
      return "destructive"
    case "SUBMITTED":
    case "APPROVED":
      return "default"
    default:
      return "secondary"
  }
}

const ACTION_LABELS: Record<RequestAction, string> = {
  SUBMIT: "Submit for review",
  APPROVE: "Approve",
  REJECT: "Reject",
  REQUEST_CHANGES: "Request changes",
}

export const ACTION_SUCCESS: Record<RequestAction, string> = {
  SUBMIT: "Request submitted for review.",
  APPROVE: "Request approved.",
  REJECT: "Request rejected.",
  REQUEST_CHANGES: "Changes requested. The creator can now edit and resubmit.",
}

export const actionLabel = (action: RequestAction) => ACTION_LABELS[action]

/** Actions that cannot be taken without explaining why. */
export const COMMENT_REQUIRED: ReadonlySet<RequestAction> = new Set(["REJECT", "REQUEST_CHANGES"])

const ACTION_PAST: Record<string, string> = {
  SUBMIT: "submitted the request",
  APPROVE: "approved the request",
  REJECT: "rejected the request",
  REQUEST_CHANGES: "requested changes",
}

/** A short phrase for a history or activity entry. targetName is who an assignment concerns. */
export function actionPastTense(action: string, targetName?: string | null): string {
  if (action === "ASSIGN") return `assigned the request to ${targetName ?? "someone"}`
  if (action === "UNASSIGN") {
    return targetName ? `removed ${targetName} as assignee` : "removed the assignee"
  }
  return ACTION_PAST[action] ?? action.toLowerCase().replaceAll("_", " ")
}

/** The actions the server offered, minus any this client does not know. */
export function knownActions(request: Pick<RequestDetail, "actions">): RequestAction[] {
  return request.actions.flatMap((action) => {
    const parsed = actionSchema.safeParse(action)
    return parsed.success ? [parsed.data] : []
  })
}
