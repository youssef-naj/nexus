import { createBrowserRouter } from "react-router"
import { AppLayout } from "@/app/AppLayout"
import { LoginPage } from "@/features/auth/LoginPage"
import { RegisterPage } from "@/features/auth/RegisterPage"
import { RequireAuth } from "@/features/auth/RequireAuth"
import { VerifyEmailPage } from "@/features/auth/VerifyEmailPage"
import { NotFoundPage } from "@/features/home/NotFoundPage"
import { CreateOrganizationPage } from "@/features/organizations/CreateOrganizationPage"
import { DashboardPage } from "@/features/dashboard/DashboardPage"
import { OrgLayout } from "@/features/organizations/OrgLayout"
import { OrganizationsPage } from "@/features/organizations/OrganizationsPage"
import { AcceptInvitationPage } from "@/features/members/AcceptInvitationPage"
import { MembersPage } from "@/features/members/MembersPage"
import { DepartmentDetailPage } from "@/features/departments/DepartmentDetailPage"
import { DepartmentsPage } from "@/features/departments/DepartmentsPage"
import { RequestDetailPage } from "@/features/requests/RequestDetailPage"
import { RequestFormPage } from "@/features/requests/RequestFormPage"
import { RequestsPage } from "@/features/requests/RequestsPage"
import { AuditPage } from "@/features/audit/AuditPage"

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  { path: "/register", element: <RegisterPage /> },
  { path: "/verify-email", element: <VerifyEmailPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: "/", element: <OrganizationsPage /> },
          { path: "/organizations/new", element: <CreateOrganizationPage /> },
          { path: "/invitations/accept", element: <AcceptInvitationPage /> },
          {
            path: "/orgs/:orgId",
            element: <OrgLayout />,
            children: [
              { index: true, element: <DashboardPage /> },
              { path: "members", element: <MembersPage /> },
              { path: "departments", element: <DepartmentsPage /> },
              { path: "departments/:departmentId", element: <DepartmentDetailPage /> },
              { path: "requests", element: <RequestsPage /> },
              { path: "requests/new", element: <RequestFormPage /> },
              { path: "requests/:requestId", element: <RequestDetailPage /> },
              { path: "requests/:requestId/edit", element: <RequestFormPage /> },
              { path: "audit", element: <AuditPage /> },
            ],
          },
        ],
      },
    ],
  },
  { path: "*", element: <NotFoundPage /> },
])
