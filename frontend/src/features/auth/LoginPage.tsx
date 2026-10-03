import { useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"
import { Link, useLocation, useNavigate } from "react-router"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { TextField } from "@/shared/components/TextField"
import { loginErrorMessage } from "./messages"
import { useLogin } from "./queries"
import { safeRedirect } from "./redirect"
import { loginSchema, type LoginForm } from "./schemas"

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const login = useLogin()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await login.mutateAsync(values)
      navigate(safeRedirect(location.state), { replace: true })
    } catch (error) {
      setFormError(loginErrorMessage(error))
    }
  })

  return (
    <main className="mx-auto grid min-h-screen max-w-md items-center p-6">
      <Card>
        <CardHeader>
          <CardTitle>
            <h1 className="text-2xl">Sign in</h1>
          </CardTitle>
          <CardDescription>Welcome back to Nexus.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} noValidate className="grid gap-4">
            {formError && (
              <Alert variant="destructive">
                <AlertDescription>{formError}</AlertDescription>
              </Alert>
            )}
            <TextField
              id="email"
              label="Email"
              type="email"
              autoComplete="username"
              error={errors.email}
              {...register("email")}
            />
            <TextField
              id="password"
              label="Password"
              type="password"
              autoComplete="current-password"
              error={errors.password}
              {...register("password")}
            />
            <Button type="submit" disabled={login.isPending}>
              {login.isPending ? "Signing in…" : "Sign in"}
            </Button>
            <p className="text-center text-sm text-muted-foreground">
              No account yet?{" "}
              <Link className="underline" to="/register">
                Create one
              </Link>
            </p>
          </form>
        </CardContent>
      </Card>
    </main>
  )
}
