import type { OrgRole } from "@/features/organizations/schemas"

/**
 * Mirrors the backend's RoleAssignmentPolicy: an Owner can grant any role, an Admin only roles
 * below Admin, everyone else none. A usability hint; the server enforces it.
 */
export function grantableRoles(actor: OrgRole): OrgRole[] {
  switch (actor) {
    case "OWNER":
      return ["OWNER", "ADMIN", "MANAGER", "EMPLOYEE"]
    case "ADMIN":
      return ["MANAGER", "EMPLOYEE"]
    default:
      return []
  }
}

/** You may only touch members whose current role you could grant yourself. */
export function canManageMember(actor: OrgRole, target: OrgRole): boolean {
  return grantableRoles(actor).includes(target)
}
