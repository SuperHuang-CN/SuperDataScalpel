export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
}

export interface CurrentUser {
  userId?: string | null;
  username: string;
  roles: string[];
  permissions: string[];
}

export interface SystemUser {
  id: string;
  username: string;
  displayName: string;
  roleId: string;
  roleName: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateSystemUserRequest {
  username: string;
  displayName: string;
  password: string;
  roleId: string;
  enabled?: boolean;
}

export interface UpdateSystemUserRequest {
  displayName: string;
  roleId: string;
  enabled: boolean;
}

export interface ResetSystemUserPasswordRequest {
  password: string;
}

export interface SystemRole {
  id: string;
  code: string;
  name: string;
  description: string | null;
  builtIn: boolean;
  permissionIds: string[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateSystemRoleRequest {
  code: string;
  name: string;
  description?: string;
}

export interface UpdateSystemRoleRequest {
  name: string;
  description?: string;
}

export interface UpdateSystemRolePermissionsRequest {
  permissionIds: string[];
}

export interface SystemPermission {
  id: string;
  code: string;
  module: string;
  name: string;
  description: string | null;
  sortOrder: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface KeywordFilter {
  keyword?: string;
}
