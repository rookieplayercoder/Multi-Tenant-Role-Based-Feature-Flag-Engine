import apiClient from './client';
import { ApiKey, ApiKeyInput } from '@/types/apiKey';

export async function getApiKeys(
  environmentId: string
): Promise<ApiKey[]> {
  const { data } = await apiClient.get<ApiKey[]>(
    `/environments/${environmentId}/api-keys`
  );
  return data;
}

export async function createApiKey(
  environmentId: string,
  payload: ApiKeyInput
): Promise<ApiKey> {
  const { data } = await apiClient.post<ApiKey>(
    `/environments/${environmentId}/api-keys`,
    payload
  );
  return data;
}

export async function deleteApiKey(
  environmentId: string,
  apiKeyId: string
): Promise<void> {
  await apiClient.delete(
    `/environments/${environmentId}/api-keys/${apiKeyId}`
  );
}
