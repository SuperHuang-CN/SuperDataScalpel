import { requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type {
  CreateSystemRoleRequest,
  CreateSystemUserRequest,
  CurrentUser,
  LoginRequest,
  LoginResponse,
  ResetSystemUserPasswordRequest,
  SystemPermission,
  SystemRole,
  SystemUser,
  UpdateSystemRolePermissionsRequest,
  UpdateSystemRoleRequest,
  UpdateSystemUserRequest,
} from '../model/systemAccess';

const SYSTEM_USERS_PATH = '/v1/system/users';
const SYSTEM_ROLES_PATH = '/v1/system/roles';
const SYSTEM_PERMISSIONS_PATH = '/v1/system/permissions';

const searchPath = (path: string, request: SearchRequest): string => {
  const query = toSearchParams(request).toString();
  return query ? `${path}?${query}` : path;
};

export const login = (request: LoginRequest): Promise<LoginResponse> => (
  requestJson<LoginResponse>('/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify(request),
    skipAuthentication: true,
  })
);

export const fetchCurrentUser = (): Promise<CurrentUser> => requestJson<CurrentUser>('/v1/auth/me');

export const fetchSystemUsers = (request: SearchRequest): Promise<PageResponse<SystemUser>> => (
  requestJson<PageResponse<SystemUser>>(searchPath(SYSTEM_USERS_PATH, request))
);

export const createSystemUser = (request: CreateSystemUserRequest): Promise<SystemUser> => (
  requestJson<SystemUser>(SYSTEM_USERS_PATH, { method: 'POST', body: JSON.stringify(request) })
);

export const updateSystemUser = (id: string, request: UpdateSystemUserRequest): Promise<SystemUser> => (
  requestJson<SystemUser>(`${SYSTEM_USERS_PATH}/${id}/actions/update`, { method: 'POST', body: JSON.stringify(request) })
);

export const resetSystemUserPassword = (id: string, request: ResetSystemUserPasswordRequest): Promise<void> => (
  requestJson<void>(`${SYSTEM_USERS_PATH}/${id}/actions/reset-password`, { method: 'POST', body: JSON.stringify(request) })
);

export const deleteSystemUser = (id: string): Promise<void> => (
  requestJson<void>(`${SYSTEM_USERS_PATH}/${id}/actions/delete`, { method: 'POST' })
);

export const fetchSystemRoles = (request: SearchRequest): Promise<PageResponse<SystemRole>> => (
  requestJson<PageResponse<SystemRole>>(searchPath(SYSTEM_ROLES_PATH, request))
);

export const createSystemRole = (request: CreateSystemRoleRequest): Promise<SystemRole> => (
  requestJson<SystemRole>(SYSTEM_ROLES_PATH, { method: 'POST', body: JSON.stringify(request) })
);

export const updateSystemRole = (id: string, request: UpdateSystemRoleRequest): Promise<SystemRole> => (
  requestJson<SystemRole>(`${SYSTEM_ROLES_PATH}/${id}/actions/update`, { method: 'POST', body: JSON.stringify(request) })
);

export const updateSystemRolePermissions = (
  id: string,
  request: UpdateSystemRolePermissionsRequest,
): Promise<SystemRole> => (
  requestJson<SystemRole>(`${SYSTEM_ROLES_PATH}/${id}/actions/update-permissions`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
);

export const deleteSystemRole = (id: string): Promise<void> => (
  requestJson<void>(`${SYSTEM_ROLES_PATH}/${id}/actions/delete`, { method: 'POST' })
);

export const fetchSystemPermissions = (request: SearchRequest): Promise<PageResponse<SystemPermission>> => (
  requestJson<PageResponse<SystemPermission>>(searchPath(SYSTEM_PERMISSIONS_PATH, request))
);
