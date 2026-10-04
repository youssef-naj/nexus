import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { BackendStatus } from "@/features/system/BackendStatus"
import { humanizePermission, roleLabel } from "./labels"
import { useOrg } from "./orgContext"

export function OrgDashboardPage() {
  const { organization } = useOrg()

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h1 className="text-2xl">{organization.name}</h1>
        </CardTitle>
        <CardDescription>
          You are signed in here as <strong>{roleLabel(organization.role)}</strong>. Members,
          departments and requests arrive in the next phases.
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        <section aria-labelledby="permissions-heading">
          <h2 id="permissions-heading" className="mb-2 text-sm font-medium">
            What your role allows
          </h2>
          <ul className="flex flex-wrap gap-2">
            {organization.permissions.map((permission) => (
              <li key={permission}>
                <Badge variant="secondary">{humanizePermission(permission)}</Badge>
              </li>
            ))}
          </ul>
        </section>
        <BackendStatus />
      </CardContent>
    </Card>
  )
}
