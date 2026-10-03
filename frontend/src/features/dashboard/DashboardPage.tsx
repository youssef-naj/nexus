import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { useCurrentUser } from "@/features/auth/queries"
import { BackendStatus } from "@/features/system/BackendStatus"

export function DashboardPage() {
  const { data: user } = useCurrentUser()
  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h1 className="text-2xl">Welcome{user ? `, ${user.displayName}` : ""}</h1>
        </CardTitle>
        <CardDescription>
          Organizations, departments and requests arrive in the next phases.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <BackendStatus />
      </CardContent>
    </Card>
  )
}
