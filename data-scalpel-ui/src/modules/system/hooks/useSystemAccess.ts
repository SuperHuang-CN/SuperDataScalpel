import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { clearAccessToken, hasAccessToken, saveAccessToken } from '../../../shared/api/http';
import type { SearchRequest } from '../../../shared/search';
import {
  createSystemRole,
  createSystemUser,
  deleteSystemRole,
  deleteSystemUser,
  fetchCurrentUser,
  fetchSystemPermissions,
  fetchSystemRoles,
  fetchSystemUsers,
  login,
  resetSystemUserPassword,
  updateSystemRole,
  updateSystemRolePermissions,
  updateSystemUser,
} from '../api/systemAccessApi';
import type {
  CreateSystemRoleRequest,
  CreateSystemUserRequest,
  LoginRequest,
  ResetSystemUserPasswordRequest,
  UpdateSystemRolePermissionsRequest,
  UpdateSystemRoleRequest,
  UpdateSystemUserRequest,
} from '../model/systemAccess';

const currentUserQueryKey = ['current-user'] as const;
const systemUsersQueryKey = ['system-users'] as const;
const systemRolesQueryKey = ['system-roles'] as const;
const systemPermissionsQueryKey = ['system-permissions'] as const;

export const useCurrentUser = () => useQuery({
  queryKey: currentUserQueryKey,
  queryFn: fetchCurrentUser,
  enabled: hasAccessToken(),
  retry: false,
});

export const useLogin = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: LoginRequest) => login(request),
    onSuccess: async (response) => {
      saveAccessToken(response.accessToken);
      await queryClient.invalidateQueries({ queryKey: currentUserQueryKey });
    },
  });
};

export const useLogout = () => {
  const queryClient = useQueryClient();
  return () => {
    clearAccessToken();
    queryClient.removeQueries();
  };
};

export const useSystemUsers = (request: SearchRequest, enabled = true) => useQuery({
  queryKey: [...systemUsersQueryKey, request],
  queryFn: () => fetchSystemUsers(request),
  enabled,
});

export const useCreateSystemUser = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateSystemUserRequest) => createSystemUser(request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: systemUsersQueryKey }),
  });
};

export const useUpdateSystemUser = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateSystemUserRequest }) => updateSystemUser(id, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: systemUsersQueryKey }),
  });
};

export const useResetSystemUserPassword = () => useMutation({
  mutationFn: ({ id, request }: { id: string; request: ResetSystemUserPasswordRequest }) => (
    resetSystemUserPassword(id, request)
  ),
});

export const useDeleteSystemUser = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteSystemUser,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: systemUsersQueryKey }),
  });
};

export const useSystemRoles = (request: SearchRequest, enabled = true) => useQuery({
  queryKey: [...systemRolesQueryKey, request],
  queryFn: () => fetchSystemRoles(request),
  enabled,
});

export const useCreateSystemRole = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateSystemRoleRequest) => createSystemRole(request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: systemRolesQueryKey }),
  });
};

export const useUpdateSystemRole = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateSystemRoleRequest }) => updateSystemRole(id, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: systemRolesQueryKey }),
  });
};

export const useUpdateSystemRolePermissions = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateSystemRolePermissionsRequest }) => (
      updateSystemRolePermissions(id, request)
    ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: systemRolesQueryKey }),
  });
};

export const useDeleteSystemRole = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteSystemRole,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: systemRolesQueryKey });
      await queryClient.invalidateQueries({ queryKey: systemUsersQueryKey });
    },
  });
};

export const useSystemPermissions = (request: SearchRequest, enabled = true) => useQuery({
  queryKey: [...systemPermissionsQueryKey, request],
  queryFn: () => fetchSystemPermissions(request),
  enabled,
});
