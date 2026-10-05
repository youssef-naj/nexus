import type { ReactElement } from "react"
import { Route, Routes } from "react-router"
import { OrgLayout } from "@/features/organizations/OrgLayout"
import { PERMISSIONS } from "@/features/organizations/permissions"
import type { OrgRole } from "@/features/organizations/schemas"
import { renderWithProviders } from "@/test/utils"

export const ORG_ID = "11111111-1111-1111-1111-111111111111"

/** The same matrix as the backend's Permission enum (Owner and Admin hold everything today). */
const PERMISSIONS_BY_ROLE: Record<OrgRole, readonly string[]> = {
  OWNER: PERMISSIONS,
  ADMIN: PERMISSIONS,
  MANAGER: [
    "ORGANIZATION_VIEW",
    "MEMBER_VIEW",
    "DEPARTMENT_VIEW",
    "REQUEST_CREATE",
    "REQUEST_VIEW_ALL",
    "REQUEST_REVIEW",
  ],
  EMPLOYEE: ["ORGANIZATION_VIEW", "MEMBER_VIEW", "DEPARTMENT_VIEW", "REQUEST_CREATE"],
}

export function orgDetail(role: OrgRole) {
  return {
    id: ORG_ID,
    name: "Acme Corp",
    slug: "acme-corp",
    role,
    status: "ACTIVE",
    permissions: PERMISSIONS_BY_ROLE[role],
  }
}

export interface MemberJson {
  id: string
  userId: string
  displayName: string
  email: string | null
  role: OrgRole
  joinedAt: string
  version: number
  you: boolean
}

export function memberJson(overrides: Partial<MemberJson> & { id: string }): MemberJson {
  return {
    userId: `user-${overrides.id}`,
    displayName: "Someone",
    email: null,
    role: "EMPLOYEE",
    joinedAt: "2026-10-01T10:00:00Z",
    version: 0,
    you: false,
    ...overrides,
  }
}

export function pageJson(content: MemberJson[], overrides: Record<string, number> = {}) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, ...overrides }
}

/** Renders a page inside the real organization layout, which supplies role and permissions. */
export function renderOrgPage(page: ReactElement) {
  return renderWithProviders(
    <Routes>
      <Route path="/" element={<p>Home</p>} />
      <Route path="/orgs/:orgId" element={<OrgLayout />}>
        <Route index element={<p>Org overview</p>} />
        <Route path="members" element={page} />
      </Route>
    </Routes>,
    [`/orgs/${ORG_ID}/members`],
  )
}
