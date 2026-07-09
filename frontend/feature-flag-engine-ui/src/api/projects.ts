import apiClient from "./client";
import { Project, ProjectInput } from "@/types/project";

export async function getProjects(
  organizationId: string
): Promise<Project[]> {
  const { data } = await apiClient.get<Project[]>(
    `/organizations/${organizationId}/projects`
  );
  return data;
}

export async function getProject(id: string): Promise<Project> {
  const { data } = await apiClient.get<Project>(`/projects/${id}`);
  return data;
}

export async function createProject(
  organizationId: string,
  payload: ProjectInput
): Promise<Project> {
  const { data } = await apiClient.post<Project>(
    `/organizations/${organizationId}/projects`,
    payload
  );
  return data;
}

export async function updateProject(
  id: string,
  payload: ProjectInput
): Promise<Project> {
  const { data } = await apiClient.put<Project>(
    `/projects/${id}`,
    payload
  );
  return data;
}

export async function deleteProject(id: string): Promise<void> {
  await apiClient.delete(`/projects/${id}`);
}