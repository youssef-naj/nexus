import type { ReactNode } from "react"
import type { Permission } from "./permissions"
import { useOrg } from "./orgContext"

/** Shows its children only if the member holds the permission. Usability, not security. */
export function Can({
  permission,
  children,
  fallback = null,
}: {
  permission: Permission
  children: ReactNode
  fallback?: ReactNode
}) {
  const { can } = useOrg()
  return <>{can(permission) ? children : fallback}</>
}
