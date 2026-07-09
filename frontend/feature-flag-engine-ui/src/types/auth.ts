export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  fullName: string;
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
  fullName: string;
  email: string;
  role?: string;
}
