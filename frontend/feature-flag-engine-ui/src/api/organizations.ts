import apiClient from './client';
import { Organization, OrganizationInput } from '@/types/organization';

/**
 * Assumes standard REST conventions on the Spring Boot side:
 * GET/POST /organizations, GET/PUT/DELETE /organizations/{id}
 * Adjust the paths below if your controller mapping differs.
 */
export async function getOrganizations(): Promise<Organization[]> {
  const { data } = await apiClient.get<Organization[]>('/organizations');
  return data;
}

export async function getOrganization(id: string): Promise<Organization> {
  const { data } = await apiClient.get<Organization>(`/organizations/${id}`);
  return data;
}

export async function createOrganization(
  payload: OrganizationInput
): Promise<Organization> {
  const { data } = await apiClient.post<Organization>('/organizations', payload);
  return data;
}

export async function updateOrganization(
  id: string,
  payload: OrganizationInput
): Promise<Organization> {
  const { data } = await apiClient.put<Organization>(
    `/organizations/${id}`,
    payload
  );
  return data;
}

export async function deleteOrganization(id: string): Promise<void> {
  await apiClient.delete(`/organizations/${id}`);
}
