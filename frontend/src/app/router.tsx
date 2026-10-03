import { createBrowserRouter } from "react-router"
import { AppLayout } from "@/app/AppLayout"
import { LoginPage } from "@/features/auth/LoginPage"
import { RegisterPage } from "@/features/auth/RegisterPage"
import { RequireAuth } from "@/features/auth/RequireAuth"
import { VerifyEmailPage } from "@/features/auth/VerifyEmailPage"
import { DashboardPage } from "@/features/dashboard/DashboardPage"
import { NotFoundPage } from "@/features/home/NotFoundPage"

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  { path: "/register", element: <RegisterPage /> },
  { path: "/verify-email", element: <VerifyEmailPage /> },
  {
    element: <RequireAuth />,
    children: [{ element: <AppLayout />, children: [{ path: "/", element: <DashboardPage /> }] }],
  },
  { path: "*", element: <NotFoundPage /> },
])
