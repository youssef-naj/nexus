import { createBrowserRouter } from "react-router"
import { AppLayout } from "@/app/AppLayout"
import { LoginPage } from "@/features/auth/LoginPage"
import { RegisterPage } from "@/features/auth/RegisterPage"
import { RequireAuth } from "@/features/auth/RequireAuth"
import { VerifyEmailPage } from "@/features/auth/VerifyEmailPage"
import { NotFoundPage } from "@/features/home/NotFoundPage"
import { CreateOrganizationPage } from "@/features/organizations/CreateOrganizationPage"
import { OrgDashboardPage } from "@/features/organizations/OrgDashboardPage"
import { OrgLayout } from "@/features/organizations/OrgLayout"
import { OrganizationsPage } from "@/features/organizations/OrganizationsPage"

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
          {
            path: "/orgs/:orgId",
            element: <OrgLayout />,
            children: [{ index: true, element: <OrgDashboardPage /> }],
          },
        ],
      },
    ],
  },
  { path: "*", element: <NotFoundPage /> },
])
