import { Link, useMatch } from "react-router"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { roleLabel } from "@/features/organizations/labels"
import { useMyOrganizations } from "@/features/organizations/queries"

export function OrgSwitcher() {
  const match = useMatch("/orgs/:orgId/*")
  const currentId = match?.params.orgId
  const { data: organizations = [] } = useMyOrganizations()
  const current = organizations.find((organization) => organization.id === currentId)

  return (
    <DropdownMenu>
      <DropdownMenuTrigger render={<Button variant="outline" size="sm" />}>
        {current ? current.name : "Organizations"}
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="min-w-56">
        <DropdownMenuGroup>
          <DropdownMenuLabel>Your organizations</DropdownMenuLabel>
          {organizations.length === 0 && (
            <p className="px-2 py-1.5 text-sm text-muted-foreground">None yet</p>
          )}
          {organizations.map((organization) => (
            <DropdownMenuItem
              key={organization.id}
              render={<Link to={`/orgs/${organization.id}`} />}
            >
              {organization.name}
              <span className="ml-auto text-xs text-muted-foreground">
                {roleLabel(organization.role)}
              </span>
            </DropdownMenuItem>
          ))}
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuItem render={<Link to="/organizations/new" />}>
          Create organization
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
