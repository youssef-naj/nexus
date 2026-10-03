import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { fetchCurrentUser, login, logout, register, verifyEmail } from "./api"
import type { LoginForm } from "./schemas"

export const meQueryKey = ["auth", "me"] as const

export function useCurrentUser() {
  return useQuery({
    queryKey: meQueryKey,
    queryFn: fetchCurrentUser,
    staleTime: 5 * 60_000,
    retry: false,
  })
}

export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (credentials: LoginForm) => {
      await login(credentials)
      return fetchCurrentUser()
    },
    onSuccess: (user) => queryClient.setQueryData(meQueryKey, user),
  })
}

export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: logout,
    // Success or failure, forget everything fetched for the previous user.
    onSettled: () => {
      queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== "auth" })
      queryClient.setQueryData(meQueryKey, null)
    },
  })
}

export function useRegister() {
  return useMutation({ mutationFn: register })
}

export function useVerifyEmail() {
  return useMutation({ mutationFn: verifyEmail })
}
