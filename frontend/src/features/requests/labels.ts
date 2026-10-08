import type { RequestCategory, RequestStatus } from "./schemas"

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
