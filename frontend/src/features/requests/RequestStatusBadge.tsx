import { Badge } from "@/components/ui/badge"
import { statusLabel, statusVariant } from "./labels"
import type { RequestStatus } from "./schemas"

export function RequestStatusBadge({ status }: { status: RequestStatus }) {
  return <Badge variant={statusVariant(status)}>{statusLabel(status)}</Badge>
}
