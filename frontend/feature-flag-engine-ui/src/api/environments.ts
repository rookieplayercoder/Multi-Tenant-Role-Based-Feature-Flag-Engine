import apiClient from "./client";
import { Environment, EnvironmentInput } from "@/types/environment";

export async function getEnvironments(
  projectId: string
): Promise<Environment[]> {
  const { data } = await apiClient.get<Environment[]>(
    `/projects/${projectId}/environments`
  );
  return data;
}

export async function getEnvironment(id: string): Promise<Environment> {
  const { data } = await apiClient.get<Environment>(`/environments/${id}`);
  return data;
}

export async function createEnvironment(
  projectId: string,
  payload: EnvironmentInput
): Promise<Environment> {
  const { data } = await apiClient.post<Environment>(
    `/projects/${projectId}/environments`,
    payload
  );
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