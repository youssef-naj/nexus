import { useState } from "react"
import { useNavigate } from "react-router"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Can } from "@/features/organizations/Can"
import { roleLabel } from "@/features/organizations/labels"
import { useOrg } from "@/features/organizations/orgContext"
import { ROLES, roleSchema, type OrgRole } from "@/features/organizations/schemas"
import { NativeSelect } from "@/shared/components/NativeSelect"
import { InvitationsSection } from "./InvitationsSection"
import { memberErrorMessage } from "./messages"
import { useChangeRole, useLeaveOrganization, useMembers, useRemoveMember } from "./queries"
import { canManageMember, grantableRoles } from "./roles"
import type { Member } from "./schemas"
import { ScrollRegion } from "@/shared/components/ScrollRegion"

export function MembersPage() {
  const { organization, can } = useOrg()
  const navigate = useNavigate()
  const orgId = organization.id
  const [page, setPage] = useState(0)
  const [roleFilter, setRoleFilter] = useState<OrgRole | "ALL">("ALL")
  const [message, setMessage] = useState<string | null>(null)
  const [confirmingLeave, setConfirmingLeave] = useState(false)

  const members = useMembers(orgId, page, roleFilter === "ALL" ? undefined : roleFilter)
  const changeRole = useChangeRole(orgId)
  const removeMember = useRemoveMember(orgId)
  const leave = useLeaveOrganization(orgId)

  const showEmails = can("MEMBER_INVITE")
  const canAssign = can("ROLE_ASSIGN")
  const canRemove = can("MEMBER_REVOKE")
  const busy = changeRole.isPending || removeMember.isPending

  async function attempt(action: () => Promise<unknown>) {
    setMessage(null)
    try {
      await action()
    } catch (error) {
      setMessage(memberErrorMessage(error))
    }
  }

  async function onLeave() {
    setMessage(null)
    try {
      await leave.mutateAsync()
      navigate("/", { replace: true })
    } catch (error) {
      setConfirmingLeave(false)
      setMessage(memberErrorMessage(error))
    }
  }

  return (
    <section className="grid gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Members</h1>
        <div className="flex items-center gap-2">
          <Label htmlFor="role-filter">Filter by role</Label>
          <NativeSelect
            id="role-filter"
            value={roleFilter}
            onChange={(event) => {
              const value = event.target.value
              setRoleFilter(value === "ALL" ? "ALL" : roleSchema.parse(value))
              setPage(0)
            }}
          >
            <option value="ALL">All roles</option>
            {ROLES.map((role) => (
              <option key={role} value={role}>
                {roleLabel(role)}
              </option>
            ))}
          </NativeSelect>
        </div>
      </div>

      {message && (
        <Alert variant="destructive">
          <AlertDescription>{message}</AlertDescription>
        </Alert>
      )}

      {members.isPending && <Skeleton className="h-40 w-full" aria-label="Loading members" />}

      {members.isError && (
        <Alert variant="destructive">
          <AlertDescription>
            <p>Couldn't load the members.</p>
            <Button className="mt-3" variant="outline" onClick={() => void members.refetch()}>
              Try again
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {members.data && (
        <>
          <ScrollRegion label="Audit events">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  {showEmails && <TableHead>Email</TableHead>}
                  <TableHead>Role</TableHead>
                  <TableHead>Joined</TableHead>
                  <TableHead>
                    <span className="sr-only">Actions</span>
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {members.data.content.map((member) => (
                  <MemberRow
                    key={member.id}
                    member={member}
                    actorRole={organization.role}
                    showEmail={showEmails}
                    canAssign={canAssign}
                    canRemove={canRemove}
                    busy={busy}
                    onChangeRole={(role) =>
                      void attempt(() =>
                        changeRole.mutateAsync({
                          membershipId: member.id,
                          role,
                          version: member.version,
                        }),
                      )
                    }
                    onRemove={() => void attempt(() => removeMember.mutateAsync(member.id))}
                  />
                ))}
              </TableBody>
            </Table>
          </ScrollRegion>

          <nav aria-label="Pagination" className="flex items-center justify-between">
            <span className="text-sm text-muted-foreground">
              Page {members.data.page + 1} of {Math.max(members.data.totalPages, 1)}
            </span>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage((current) => Math.max(current - 1, 0))}
              >
                Previous page
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={page + 1 >= members.data.totalPages}
                onClick={() => setPage((current) => current + 1)}
              >
                Next page
              </Button>
            </div>
          </nav>
        </>
      )}

      <Can permission="MEMBER_INVITE">
        <InvitationsSection />
      </Can>

      <div className="flex items-center gap-3 border-t pt-4">
        {confirmingLeave ? (
          <>
            <span className="text-sm">Leave {organization.name}?</span>
            <Button
              variant="destructive"
              size="sm"
              disabled={leave.isPending}
              onClick={() => void onLeave()}
            >
              Confirm leaving
            </Button>
            <Button variant="ghost" size="sm" onClick={() => setConfirmingLeave(false)}>
              Cancel
            </Button>
          </>
        ) : (
          <Button variant="outline" size="sm" onClick={() => setConfirmingLeave(true)}>
            Leave organization
          </Button>
        )}
      </div>
    </section>
  )
}

interface MemberRowProps {
  member: Member
  actorRole: OrgRole
  showEmail: boolean
  canAssign: boolean
  canRemove: boolean
  busy: boolean
  onChangeRole: (role: OrgRole) => void
  onRemove: () => void
}

function MemberRow({
  member,
  actorRole,
  showEmail,
  canAssign,
  canRemove,
  busy,
  onChangeRole,
  onRemove,
}: MemberRowProps) {
  const [confirming, setConfirming] = useState(false)
  // Hints only: nobody manages themselves, and you may only touch roles you could grant
  const manageable = !member.you && canManageMember(actorRole, member.role)

  return (
    <TableRow>
      <TableCell className="font-medium">
        {member.displayName}
        {member.you && <span className="ml-2 text-xs text-muted-foreground">(you)</span>}
      </TableCell>
      {showEmail && <TableCell>{member.email ?? ""}</TableCell>}
      <TableCell>
        {manageable && canAssign ? (
          <NativeSelect
            aria-label={`Role of ${member.displayName}`}
            value={member.role}
            disabled={busy}
            onChange={(event) => onChangeRole(roleSchema.parse(event.target.value))}
          >
            {grantableRoles(actorRole).map((role) => (
              <option key={role} value={role}>
                {roleLabel(role)}
              </option>
            ))}
          </NativeSelect>
        ) : (
          <Badge variant="secondary">{roleLabel(member.role)}</Badge>
        )}
      </TableCell>
      <TableCell>{new Date(member.joinedAt).toLocaleDateString()}</TableCell>
      <TableCell className="text-right">
        {manageable && canRemove && (
          <span className="inline-flex gap-2">
            {confirming ? (
              <>
                <Button
                  variant="destructive"
                  size="sm"
                  disabled={busy}
                  aria-label={`Confirm removal of ${member.displayName}`}
                  onClick={() => {
                    setConfirming(false)
                    onRemove()
                  }}
                >
                  Confirm
                </Button>
                <Button variant="ghost" size="sm" onClick={() => setConfirming(false)}>
                  Cancel
                </Button>
              </>
            ) : (
              <Button
                variant="outline"
                size="sm"
                aria-label={`Remove ${member.displayName}`}
                onClick={() => setConfirming(true)}
              >
                Remove
              </Button>
            )}
          </span>
        )}
      </TableCell>
    </TableRow>
  )
}
