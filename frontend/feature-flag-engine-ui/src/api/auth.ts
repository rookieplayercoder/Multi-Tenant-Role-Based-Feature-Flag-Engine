import apiClient from './client';
import { LoginRequest, LoginResponse } from '@/types/auth';

/**
 * NOTE: We weren't told the exact shape of your Spring Boot login response,
 * so this reads either `token` or `accessToken` from the JSON body.
 * Adjust this file if your backend returns the JWT differently
 * (e.g. in an Authorization response header).
 */
export async function login(payload: LoginRequest): Promise<LoginResponse> {
  const { data } = await apiClient.post<Record<string, unknown>>(
    '/auth/login',
    payload
  );

  const token = (data.token || data.accessToken) as string | undefined;

  if (!token) {
    throw new Error('Login succeeded but no token was returned by the API.');
  }

  return {
    token,
    refreshToken: data.refreshToken as string | undefined,
    user: data.user as LoginResponse['user'],
  };
}
