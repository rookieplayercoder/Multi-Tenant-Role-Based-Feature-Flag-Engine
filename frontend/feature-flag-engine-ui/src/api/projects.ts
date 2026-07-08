import apiClient from './client';
import { Project, ProjectInput } from '@/types/project';

/**
 * Assumes a flat /projects resource where each project carries an
 * organizationId, and standard REST conventions:
 * GET/POST /projects, GET/PUT/DELETE /projects/{id}.
 * If your API instead nests projects under an organization
 * (e.g. /organizations/{orgId}/projects), update the paths below.
 */
export async function getProjects(organizationId?: string): Promise<Project[]> {
  const { data } = await apiClient.get<Project[]>('/projects', {
    params: organizationId ? { organizationId } : undefined,
  });
  return data;
}

export async function getProject(id: string): Promise<Project> {
  const { data } = await apiClient.get<Project>(`/projects/${id}`);
  return data;
}

export async function createProject(payload: ProjectInput): Promise<Project> {
  const { data } = await apiClient.post<Project>('/projects', payload);
  return data;
}

export async function updateProject(
  id: string,
  payload: ProjectInput
): Promise<Project> {
  const { data } = await apiClient.put<Project>(`/projects/${id}`, payload);
  return data;
}

export async function deleteProject(id: string): Promise<void> {
  await apiClient.delete(`/projects/${id}`);
}
