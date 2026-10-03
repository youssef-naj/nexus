import { Link, useSearchParams } from "react-router"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { ApiError } from "@/shared/api/client"
import { genericErrorMessage } from "./messages"
import { useVerifyEmail } from "./queries"

export function VerifyEmailPage() {
  const [params] = useSearchParams()
  const token = params.get("token")
  const verify = useVerifyEmail()

  return (
    <main className="mx-auto grid min-h-screen max-w-md items-center p-6">
      <Card>
        <CardHeader>
          <CardTitle>
            <h1 className="text-2xl">Verify your email</h1>
          </CardTitle>
          <CardDescription>Confirm that this address belongs to you.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          {!token && (
            <Alert variant="destructive">
              <AlertTitle>Incomplete link</AlertTitle>
              <AlertDescription>
                This verification link is incomplete.{" "}
                <Link className="underline" to="/register">
                  Register again
                </Link>{" "}
                to receive a new one.
              </AlertDescription>
            </Alert>
          )}

          {token && !verify.isSuccess && (
            <>
              {verify.isError && (
                <Alert variant="destructive">
                  <AlertDescription>
                    {verify.error instanceof ApiError && verify.error.status === 400
                      ? "This link is invalid or has expired. Register again to receive a new one."
                      : genericErrorMessage(verify.error)}
                  </AlertDescription>
                </Alert>
              )}
              {/* The token is only used when the person clicks, so link scanners cannot consume it. */}
              <Button disabled={verify.isPending} onClick={() => verify.mutate(token)}>
                {verify.isPending ? "Confirming…" : "Confirm email"}
              </Button>
            </>
          )}

          {verify.isSuccess && (
            <Alert>
              <AlertTitle>Email verified</AlertTitle>
              <AlertDescription>
                Your address is confirmed.{" "}
                <Link className="underline" to="/login">
                  Continue to sign in
                </Link>
              </AlertDescription>
            </Alert>
          )}
        </CardContent>
      </Card>
    </main>
  )
}
