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
