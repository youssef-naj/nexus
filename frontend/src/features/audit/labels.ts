import { roleLabel } from "@/features/organizations/labels"
import { roleSchema } from "@/features/organizations/schemas"

export const EVENT_LABELS: Record<string, string> = {
  USER_EMAIL_VERIFIED: "Email verified",
  ORGANIZATION_CREATED: "Organization created",
  INVITATION_CREATED: "Invitation sent",
  INVITATION_REVOKED: "Invitation revoked",
  INVITATION_ACCEPTED: "Invitation accepted",
  INVITATION_REJECTED: "Invitation declined",
  MEMBER_ROLE_CHANGED: "Member role changed",
  MEMBER_REMOVED: "Member removed",
  MEMBER_LEFT: "Member left",
  DEPARTMENT_CREATED: "Department created",
  DEPARTMENT_UPDATED: "Department updated",
  DEPARTMENT_DEACTIVATED: "Department deactivated",
  DEPARTMENT_REACTIVATED: "Department reactivated",
  DEPARTMENT_MEMBER_ADDED: "Member added to department",
  DEPARTMENT_MEMBER_REMOVED: "Member removed from department",
  REQUEST_SUBMITTED: "Request submitted",
  REQUEST_APPROVED: "Request approved",
  REQUEST_REJECTED: "Request rejected",
  REQUEST_CHANGES_REQUESTED: "Changes requested",
}

export const EVENT_TYPES = Object.keys(EVENT_LABELS)

export function eventLabel(eventType: string): string {
  const known = EVENT_LABELS[eventType]
  if (known) return known
  const words = eventType.toLowerCase().replaceAll("_", " ")
  return words.charAt(0).toUpperCase() + words.slice(1)
}

function role(value: string): string {
  const parsed = roleSchema.safeParse(value)
  return parsed.success ? roleLabel(parsed.data) : value
}

/** A short human summary of the allow-listed metadata. Identifiers are deliberately not shown. */
export function describeMetadata(metadata: Record<string, string>): string {
  const parts: string[] = []
  if (metadata["reference"]) parts.push(`Request: ${metadata["reference"]}`)
  if (metadata["name"]) parts.push(`Name: ${metadata["name"]}`)
  if (metadata["slug"]) parts.push(`Slug: ${metadata["slug"]}`)
  if (metadata["fromName"] && metadata["toName"]) {
    parts.push(`Renamed: ${metadata["fromName"]} → ${metadata["toName"]}`)
  }
  if (metadata["fromRole"] && metadata["toRole"]) {
    parts.push(`Role: ${role(metadata["fromRole"])} → ${role(metadata["toRole"])}`)
  } else if (metadata["role"]) {
    parts.push(`Role: ${role(metadata["role"])}`)
  }
  return parts.join(" · ")
}
