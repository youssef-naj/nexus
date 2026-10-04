import { createContext, useContext } from "react"
import type { Permission } from "./permissions"
import type { OrganizationDetail } from "./schemas"

export interface OrgContextValue {
  organization: OrganizationDetail
  /** UI hint only. The server enforces every permission on every request. */
  can: (permission: Permission) => boolean
}

export const OrgContext = createContext<OrgContextValue | null>(null)

export function useOrg(): OrgContextValue {
  const value = useContext(OrgContext)
  if (!value) throw new Error("useOrg must be used inside the organization layout")
  return value
}
