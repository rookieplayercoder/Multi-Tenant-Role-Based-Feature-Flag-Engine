import apiClient from './client';
import { Environment, EnvironmentInput } from '@/types/environment';

/**
 * Assumes a flat /environments resource where each environment carries a
 * projectId, and standard REST conventions:
 * GET/POST /environments, GET/PUT/DELETE /environments/{id}.
 * If your API instead nests environments under a project
 * (e.g. /projects/{projectId}/environments), update the paths below.
 */
export async function getEnvironments(projectId?: string): Promise<Environment[]> {
  const { data } = await apiClient.get<Environment[]>('/environments', {
    params: projectId ? { projectId } : undefined,
  });
  return data;
}

export async function getEnvironment(id: string): Promise<Environment> {
  const { data } = await apiClient.get<Environment>(`/environments/${id}`);
  return data;
}

export async function createEnvironment(
  payload: EnvironmentInput
): Promise<Environment> {
  const { data } = await apiClient.post<Environment>('/environments', payload);
  return data;
}

export async function updateEnvironment(
  id: string,
  payload: EnvironmentInput
): Promise<Environment> {
  const { data } = await apiClient.put<Environment>(
    `/environments/${id}`,
    payload
  );
  return data;
}

export async function deleteEnvironment(id: string): Promise<void> {
  await apiClient.delete(`/environments/${id}`);
}
