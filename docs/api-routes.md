# API routes

Generated from the code by `ApiRouteDocumentationTest`; do not edit by hand. Regenerate with `NEXUS_DOCS_WRITE=true` when routes change.

Errors are RFC 9457 Problem Details. Every state-changing call needs the CSRF header. Outside the routes below, Spring Security handles `POST /api/auth/login` (form fields `email` and `password`, public) and `POST /api/auth/logout` (signed in). Health endpoints under `/api/actuator` are for the orchestrator and are not exposed through the proxy.

| Method | Path | Who may call it | Handler |
|---|---|---|---|
| `GET` | `/api/auth/me` | Signed-in users | `AuthController.me` |
| `POST` | `/api/auth/register` | Anyone | `AuthController.register` |
| `POST` | `/api/auth/verify-email` | Anyone | `AuthController.verifyEmail` |
| `POST` | `/api/invitations/accept` | Signed-in users | `InvitationAcceptanceController.accept` |
| `POST` | `/api/invitations/preview` | Signed-in users | `InvitationAcceptanceController.preview` |
| `POST` | `/api/invitations/reject` | Signed-in users | `InvitationAcceptanceController.reject` |
| `GET` | `/api/orgs` | Signed-in users | `OrganizationController.mine` |
| `POST` | `/api/orgs` | Signed-in users | `OrganizationController.create` |
| `GET` | `/api/orgs/{orgId}` | Active members of the organization; the permission is checked per action | `OrganizationController.details` |
| `GET` | `/api/orgs/{orgId}/audit` | Active members of the organization; the permission is checked per action | `AuditController.list` |
| `GET` | `/api/orgs/{orgId}/dashboard` | Active members of the organization; the permission is checked per action | `DashboardController.get` |
| `GET` | `/api/orgs/{orgId}/departments` | Active members of the organization; the permission is checked per action | `DepartmentController.list` |
| `POST` | `/api/orgs/{orgId}/departments` | Active members of the organization; the permission is checked per action | `DepartmentController.create` |
| `GET` | `/api/orgs/{orgId}/departments/{departmentId}` | Active members of the organization; the permission is checked per action | `DepartmentController.get` |
| `PUT` | `/api/orgs/{orgId}/departments/{departmentId}` | Active members of the organization; the permission is checked per action | `DepartmentController.update` |
| `POST` | `/api/orgs/{orgId}/departments/{departmentId}/deactivate` | Active members of the organization; the permission is checked per action | `DepartmentController.deactivate` |
| `GET` | `/api/orgs/{orgId}/departments/{departmentId}/members` | Active members of the organization; the permission is checked per action | `DepartmentMemberController.members` |
| `DELETE` | `/api/orgs/{orgId}/departments/{departmentId}/members/{membershipId}` | Active members of the organization; the permission is checked per action | `DepartmentMemberController.unassign` |
| `PUT` | `/api/orgs/{orgId}/departments/{departmentId}/members/{membershipId}` | Active members of the organization; the permission is checked per action | `DepartmentMemberController.assign` |
| `POST` | `/api/orgs/{orgId}/departments/{departmentId}/reactivate` | Active members of the organization; the permission is checked per action | `DepartmentController.reactivate` |
| `GET` | `/api/orgs/{orgId}/invitations` | Active members of the organization; the permission is checked per action | `InvitationController.pending` |
| `POST` | `/api/orgs/{orgId}/invitations` | Active members of the organization; the permission is checked per action | `InvitationController.create` |
| `DELETE` | `/api/orgs/{orgId}/invitations/{invitationId}` | Active members of the organization; the permission is checked per action | `InvitationController.revoke` |
| `POST` | `/api/orgs/{orgId}/leave` | Active members of the organization; the permission is checked per action | `MemberController.leave` |
| `GET` | `/api/orgs/{orgId}/members` | Active members of the organization; the permission is checked per action | `MemberController.list` |
| `DELETE` | `/api/orgs/{orgId}/members/{membershipId}` | Active members of the organization; the permission is checked per action | `MemberController.remove` |
| `PATCH` | `/api/orgs/{orgId}/members/{membershipId}` | Active members of the organization; the permission is checked per action | `MemberController.changeRole` |
| `GET` | `/api/orgs/{orgId}/members/{membershipId}/departments` | Active members of the organization; the permission is checked per action | `DepartmentMemberController.departmentsOf` |
| `GET` | `/api/orgs/{orgId}/requests` | Active members of the organization; the permission is checked per action | `RequestController.list` |
| `POST` | `/api/orgs/{orgId}/requests` | Active members of the organization; the permission is checked per action | `RequestController.create` |
| `GET` | `/api/orgs/{orgId}/requests/{requestId}` | Active members of the organization; the permission is checked per action | `RequestController.get` |
| `PUT` | `/api/orgs/{orgId}/requests/{requestId}` | Active members of the organization; the permission is checked per action | `RequestController.update` |
| `DELETE` | `/api/orgs/{orgId}/requests/{requestId}/assignee` | Active members of the organization; the permission is checked per action | `RequestController.unassign` |
| `PUT` | `/api/orgs/{orgId}/requests/{requestId}/assignee` | Active members of the organization; the permission is checked per action | `RequestController.assign` |
| `GET` | `/api/orgs/{orgId}/requests/{requestId}/events` | Active members of the organization; the permission is checked per action | `RequestController.events` |
| `POST` | `/api/orgs/{orgId}/requests/{requestId}/transitions` | Active members of the organization; the permission is checked per action | `RequestController.transition` |
| `GET` | `/api/orgs/{orgId}/reviewers` | Active members of the organization; the permission is checked per action | `ReviewerController.list` |
| `GET` | `/api/platform/audit` | Platform administrators (anyone else: `404`) | `PlatformAuditController.list` |
| `GET` | `/api/platform/organizations` | Platform administrators (anyone else: `404`) | `PlatformOrganizationController.list` |
| `POST` | `/api/platform/organizations/{organizationId}/reactivate` | Platform administrators (anyone else: `404`) | `PlatformOrganizationController.reactivate` |
| `POST` | `/api/platform/organizations/{organizationId}/suspend` | Platform administrators (anyone else: `404`) | `PlatformOrganizationController.suspend` |
| `GET` | `/api/system/ping` | Anyone | `SystemController.ping` |
