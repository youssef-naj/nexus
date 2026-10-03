import { useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"
import { Link } from "react-router"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { TextField } from "@/shared/components/TextField"
import { applyServerErrors } from "@/shared/forms/serverErrors"
import { genericErrorMessage } from "./messages"
import { useRegister } from "./queries"
import { registerSchema, type RegisterForm } from "./schemas"

export function RegisterPage() {
  const registration = useRegister()
  const [submitted, setSubmitted] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<RegisterForm>({
    resolver: zodResolver(registerSchema),
    defaultValues: { displayName: "", email: "", password: "" },
  })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await registration.mutateAsync(values)
      setSubmitted(true)
    } catch (error) {
      const shown = applyServerErrors(error, setError, ["displayName", "email", "password"])
      if (!shown) setFormError(genericErrorMessage(error))
    }
  })

  if (submitted) {
    return (
      <main className="mx-auto grid min-h-screen max-w-md items-center p-6">
        <Card>
          <CardHeader>
            <CardTitle>
              <h1 className="text-2xl">Check your email</h1>
            </CardTitle>
            <CardDescription>
              If the address can be used, a message with the next steps is on its way. The link
              expires in 24 hours.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Link className="underline" to="/login">
              Back to sign in
            </Link>
          </CardContent>
        </Card>
      </main>
    )
  }

  return (
    <main className="mx-auto grid min-h-screen max-w-md items-center p-6">
      <Card>
        <CardHeader>
          <CardTitle>
            <h1 className="text-2xl">Create your account</h1>
          </CardTitle>
          <CardDescription>You will confirm your email address next.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} noValidate className="grid gap-4">
            {formError && (
              <Alert variant="destructive">
                <AlertDescription>{formError}</AlertDescription>
              </Alert>
            )}
            <TextField
              id="displayName"
              label="Name"
              autoComplete="name"
              error={errors.displayName}
              {...register("displayName")}
            />
            <TextField
              id="email"
              label="Email"
              type="email"
              autoComplete="email"
              error={errors.email}
              {...register("email")}
            />
            <TextField
              id="password"
              label="Password"
              type="password"
              autoComplete="new-password"
              hint="At least 12 characters. A few random words make a strong passphrase."
              error={errors.password}
              {...register("password")}
            />
            <Button type="submit" disabled={registration.isPending}>
              {registration.isPending ? "Creating account…" : "Create account"}
            </Button>
            <p className="text-center text-sm text-muted-foreground">
              Already registered?{" "}
              <Link className="underline" to="/login">
                Sign in
              </Link>
            </p>
          </form>
        </CardContent>
      </Card>
    </main>
  )
}
