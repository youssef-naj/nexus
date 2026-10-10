import { createBrowserRouter } from "react-router"
import { AppLayout } from "@/app/AppLayout"
import { RootLayout } from "@/app/RootLayout"
import { AuditPage } from "@/features/audit/AuditPage"
import { LoginPage } from "@/features/auth/LoginPage"
import { RegisterPage } from "@/features/auth/RegisterPage"
import { RequireAuth } from "@/features/auth/RequireAuth"
import { VerifyEmailPage } from "@/features/auth/VerifyEmailPage"
import { DashboardPage } from "@/features/dashboard/DashboardPage"
import { DepartmentDetailPage } from "@/features/departments/DepartmentDetailPage"
import { DepartmentsPage } from "@/features/departments/DepartmentsPage"
import { NotFoundPage } from "@/features/home/NotFoundPage"
import { AcceptInvitationPage } from "@/features/members/AcceptInvitationPage"
import { MembersPage } from "@/features/members/MembersPage"
import { CreateOrganizationPage } from "@/features/organizations/CreateOrganizationPage"
import { OrgLayout } from "@/features/organizations/OrgLayout"
import { OrganizationsPage } from "@/features/organizations/OrganizationsPage"
import { RequestDetailPage } from "@/features/requests/RequestDetailPage"
import { RequestFormPage } from "@/features/requests/RequestFormPage"
import { RequestsPage } from "@/features/requests/RequestsPage"

/** Each route's `handle.title` becomes the page title (see RootLayout). */
export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    children: [
      { path: "/login", element: <LoginPage />, handle: { title: "Sign in" } },
      { path: "/register", element: <RegisterPage />, handle: { title: "Create account" } },
      { path: "/verify-email", element: <VerifyEmailPage />, handle: { title: "Verify email" } },
      {
        element: <RequireAuth />,
        children: [
          {
            element: <AppLayout />,
            children: [
              {
                path: "/",
                element: <OrganizationsPage />,
                handle: { title: "Your organizations" },
              },
              {
                path: "/organizations/new",
                element: <CreateOrganizationPage />,
                handle: { title: "New organization" },
              },
              {
                path: "/invitations/accept",
                element: <AcceptInvitationPage />,
                handle: { title: "Accept invitation" },
              },
              {
                path: "/orgs/:orgId",
                element: <OrgLayout />,
                children: [
                  { index: true, element: <DashboardPage />, handle: { title: "Dashboard" } },
                  { path: "members", element: <MembersPage />, handle: { title: "Members" } },
                  {
                    path: "departments",
                    element: <DepartmentsPage />,
                    handle: { title: "Departments" },
                  },
                  {
                    path: "departments/:departmentId",
                    element: <DepartmentDetailPage />,
                    handle: { title: "Department" },
                  },
                  { path: "requests", element: <RequestsPage />, handle: { title: "Requests" } },
                  {
                    path: "requests/new",
                    element: <RequestFormPage />,
                    handle: { title: "New request" },
                  },
                  {
                    path: "requests/:requestId",
                    element: <RequestDetailPage />,
                    handle: { title: "Request" },
                  },
                  {
                    path: "requests/:requestId/edit",
                    element: <RequestFormPage />,
                    handle: { title: "Edit request" },
                  },
                  { path: "audit", element: <AuditPage />, handle: { title: "Audit log" } },
                ],
              },
            ],
          },
        ],
      },
      { path: "*", element: <NotFoundPage />, handle: { title: "Page not found" } },
    ],
  },
])
