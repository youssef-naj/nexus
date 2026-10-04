import { useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"
import { Link, useNavigate } from "react-router"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button, buttonVariants } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { genericErrorMessage } from "@/features/auth/messages"
import { TextField } from "@/shared/components/TextField"
import { applyServerErrors } from "@/shared/forms/serverErrors"
import { useCreateOrganization } from "./queries"
import { createOrganizationSchema, type CreateOrganizationForm } from "./schemas"

export function CreateOrganizationPage() {
  const navigate = useNavigate()
  const create = useCreateOrganization()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<CreateOrganizationForm>({
    resolver: zodResolver(createOrganizationSchema),
    defaultValues: { name: "" },
  })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      const created = await create.mutateAsync(values.name)
      navigate(`/orgs/${created.id}`, { replace: true })
    } catch (error) {
      if (!applyServerErrors(error, setError, ["name"])) {
        setFormError(genericErrorMessage(error))
      }
    }
  })

  return (
    <Card className="mx-auto max-w-md">
      <CardHeader>
        <CardTitle>
          <h1 className="text-2xl">Create an organization</h1>
        </CardTitle>
        <CardDescription>You will become its owner.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} noValidate className="grid gap-4">
          {formError && (
            <Alert variant="destructive">
              <AlertDescription>{formError}</AlertDescription>
            </Alert>
          )}
          <TextField
            id="name"
            label="Organization name"
            autoComplete="organization"
            error={errors.name}
            {...register("name")}
          />
          <div className="flex gap-3">
            <Button type="submit" disabled={create.isPending}>
              {create.isPending ? "Creating…" : "Create organization"}
            </Button>
            <Link className={buttonVariants({ variant: "ghost" })} to="/">
              Cancel
            </Link>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}
