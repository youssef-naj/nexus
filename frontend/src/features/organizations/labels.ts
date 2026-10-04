import type { OrgRole } from "./schemas"

export function roleLabel(role: OrgRole): string {
  return role.charAt(0) + role.slice(1).toLowerCase()
}

export function humanizePermission(permission: string): string {
  const words = permission.toLowerCase().replaceAll("_", " ")
  return words.charAt(0).toUpperCase() + words.slice(1)
}
