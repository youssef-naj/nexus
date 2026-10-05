import { useState } from "react"
import { Link, useNavigate, useSearchParams } from "react-router"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { roleLabel } from "@/features/organizations/labels"
import { ApiError } from "@/shared/api/client"
import { memberErrorMessage } from "./messages"
import { useAcceptInvitation, useInvitationPreview, useRejectInvitation } from "./queries"

export function AcceptInvitationPage() {
  const [params] = useSearchParams()
  const token = params.get("token")
  const navigate = useNavigate()
  const preview = useInvitationPreview(token)
  const accept = useAcceptInvitation()
  const reject = useRejectInvitation()
  const [message, setMessage] = useState<string | null>(null)
  const [declined, setDeclined] = useState(false)

  if (!token) {
    return (
      <Alert variant="destructive">
        <AlertTitle>Incomplete link</AlertTitle>
        <AlertDescription>
          This invitation link is incomplete. Open the link from your invitation email again.
        </AlertDescription>
      </Alert>
    )
  }

  if (declined) {
    return (
      <Alert>
        <AlertTitle>Invitation declined</AlertTitle>
        <AlertDescription>
          <Link className="underline" to="/">
            Back to your organizations
          </Link>
        </AlertDescription>
      </Alert>
    )
  }

  if (preview.isPending) {
    return <Skeleton className="h-24 w-full max-w-md" aria-label="Checking the invitation" />
  }

  if (preview.error) {
    const unusable = preview.error instanceof ApiError && preview.error.status === 400
    return (
      <Alert variant="destructive">
        <AlertTitle>
          {unusable ? "This invitation can't be used" : "Couldn't check the invitation"}
        </AlertTitle>
        <AlertDescription>
          {unusable ? (
            <p>
              It may have expired, been withdrawn or already been used, or it was sent to a
              different email address. Make sure you are signed in with the address the invitation
              was sent to.
            </p>
          ) : (
            <p>{memberErrorMessage(preview.error)}</p>
          )}
          <Link className="mt-2 inline-block underline" to="/">
            Back to your organizations
          </Link>
        </AlertDescription>
      </Alert>
    )
  }

  async function onAccept() {
    setMessage(null)
    try {
      const joined = await accept.mutateAsync(token ?? "")
      navigate(`/orgs/${joined.organizationId}`, { replace: true })
    } catch (error) {
      setMessage(memberErrorMessage(error))
    }
  }

  async function onDecline() {
    setMessage(null)
    try {
      await reject.mutateAsync(token ?? "")
      setDeclined(true)
    } catch (error) {
      setMessage(memberErrorMessage(error))
    }
  }

  const { organizationName, role } = preview.data
  const busy = accept.isPending || reject.isPending

  return (
    <Card className="max-w-md">
      <CardHeader>
        <CardTitle>
          <h1 className="text-2xl">Join {organizationName}</h1>
        </CardTitle>
        <CardDescription>
          You've been invited to join <strong>{organizationName}</strong> as{" "}
          <strong>{roleLabel(role)}</strong>.
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        {message && (
          <Alert variant="destructive">
            <AlertDescription>{message}</AlertDescription>
          </Alert>
        )}
        <div className="flex gap-3">
          <Button disabled={busy} onClick={() => void onAccept()}>
            {accept.isPending ? "Joining…" : "Accept invitation"}
          </Button>
          <Button variant="outline" disabled={busy} onClick={() => void onDecline()}>
            Decline
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
