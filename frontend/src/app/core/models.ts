export interface AuthUser {
  id: number;
  name: string;
  email: string;
  jobTitle: string | null;
  owner: boolean;
  active: boolean;
  roles: string[];
  permissions: string[];
}

export interface LoginRequest {
  tenant: string;
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  user: AuthUser;
}

/* ---- Usuários (M1) ---- */
export interface UserView {
  id: number;
  name: string;
  email: string;
  jobTitle: string | null;
  owner: boolean;
  active: boolean;
  roles: string[];
}

export interface CreateUserRequest {
  name: string;
  email: string;
  jobTitle: string | null;
  roles: string[];
}

export interface UpdateUserRequest {
  name: string;
  jobTitle: string | null;
  roles: string[];
  active: boolean;
}

export interface CreatedUser {
  user: UserView;
  initialPassword: string;
}

/* ---- Papéis & permissões (M1) ---- */
export interface RoleView {
  id: number;
  name: string;
  label: string;
  system: boolean;
  permissions: string[];
}

export interface PermissionGroup {
  group: string;
  permissions: string[];
}

export interface PermissionCatalog {
  groups: PermissionGroup[];
}

export interface SaveRoleRequest {
  name: string;
  label: string;
  permissions: string[];
}
