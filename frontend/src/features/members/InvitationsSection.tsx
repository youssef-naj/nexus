import { useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import { roleLabel } from "@/features/organizations/labels"
import { useOrg } from "@/features/organizations/orgContext"
import { NativeSelect } from "@/shared/components/NativeSelect"
import { TextField } from "@/shared/components/TextField"
import { applyServerErrors } from "@/shared/forms/serverErrors"
import { memberErrorMessage } from "./messages"
import { useCreateInvitation, useInvitations, useRevokeInvitation } from "./queries"
import { grantableRoles } from "./roles"
import { inviteSchema, type InviteForm } from "./schemas"

export function InvitationsSection() {
  const { organization } = useOrg()
  const orgId = organization.id
  const roles = grantableRoles(organization.role)
  const invitations = useInvitations(orgId)
  const create = useCreateInvitation(orgId)
  const revoke = useRevokeInvitation(orgId)
  const [sentTo, setSentTo] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [listError, setListError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<InviteForm>({
    resolver: zodResolver(inviteSchema),
    defaultValues: { email: "", role: "EMPLOYEE" },
  })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    setSentTo(null)
    try {
      await create.mutateAsync(values)
      setSentTo(values.email)
      reset({ email: "", role: values.role })
    } catch (error) {
      if (!applyServerErrors(error, setError, ["email", "role"])) {
        setFormError(memberErrorMessage(error))
      }
    }
  })

  async function onRevoke(invitationId: string) {
    setListError(null)
    try {
      await revoke.mutateAsync(invitationId)
    } catch (error) {
      setListError(memberErrorMessage(error))
    }
  }

  return (
    <section aria-labelledby="invite-heading" className="grid gap-4 border-t pt-6">
      <h2 id="invite-heading" className="text-lg font-semibold">
        Invite someone
      </h2>

      <form onSubmit={onSubmit} noValidate className="grid max-w-md gap-4">
        {formError && (
          <Alert variant="destructive">
            <AlertDescription>{formError}</AlertDescription>
          </Alert>
        )}
        {sentTo && (
          <Alert role="status">
            <AlertDescription>Invitation sent to {sentTo}.</AlertDescription>
          </Alert>
        )}
        <TextField
          id="invite-email"
          label="Email address"
          type="email"
          autoComplete="off"
          error={errors.email}
          {...register("email")}
        />
        <div className="grid gap-2">
          <Label htmlFor="invite-role">Role</Label>
          <NativeSelect id="invite-role" {...register("role")}>
            {roles.map((role) => (
              <option key={role} value={role}>
                {roleLabel(role)}
              </option>
            ))}
          </NativeSelect>
        </div>
        <Button type="submit" disabled={create.isPending} className="justify-self-start">
          {create.isPending ? "Sending…" : "Send invitation"}
        </Button>
      </form>

      <div className="grid gap-2">
        <h3 className="text-sm font-medium">Pending invitations</h3>
        {listError && (
          <Alert variant="destructive">
            <AlertDescription>{listError}</AlertDescription>
          </Alert>
        )}
        {invitations.isPending && (
          <Skeleton className="h-10 w-full" aria-label="Loading invitations" />
        )}
        {invitations.isError && (
          <p className="text-sm text-destructive">Couldn't load the pending invitations.</p>
        )}
        {invitations.data?.length === 0 && (
          <p className="text-sm text-muted-foreground">No pending invitations.</p>
        )}
        <ul className="grid gap-2">
          {invitations.data?.map((invitation) => (
            <li
              key={invitation.id}
              className="flex flex-wrap items-center justify-between gap-2 rounded-lg border p-3"
            >
              <span className="flex flex-wrap items-center gap-2">
                <span className="font-medium">{invitation.email}</span>
                <Badge variant="secondary">{roleLabel(invitation.role)}</Badge>
                {invitation.expired ? (
                  <Badge variant="destructive">Expired</Badge>
                ) : (
                  <span className="text-xs text-muted-foreground">
                    expires {new Date(invitation.expiresAt).toLocaleDateString()}
                  </span>
                )}
              </span>
              {roles.includes(invitation.role) && (
                <Button
                  variant="outline"
                  size="sm"
                  disabled={revoke.isPending}
                  aria-label={`Revoke invitation for ${invitation.email}`}
                  onClick={() => void onRevoke(invitation.id)}
                >
                  Revoke
                </Button>
              )}
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}
