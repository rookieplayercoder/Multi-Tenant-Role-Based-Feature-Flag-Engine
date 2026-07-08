export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  refreshToken?: string;
  user?: AuthUser;
}

export interface AuthUser {
  id: string;
  name: string;
  email: string;
  role?: string;
}
