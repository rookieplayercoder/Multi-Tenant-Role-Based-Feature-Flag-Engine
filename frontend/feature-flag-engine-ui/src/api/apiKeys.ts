import apiClient from './client';
import { ApiKey, ApiKeyInput } from '@/types/apiKey';

/**
 * Assumes a flat /api-keys resource where each key carries an
 * environmentId, using standard REST conventions:
 *   GET    /api-keys?environmentId=...  -> ApiKey[] (masked, no secret)
 *   POST   /api-keys                    -> ApiKey (includes the full secret ONCE)
 *   PUT    /api-keys/{id}               -> rename only, { name }
 *   DELETE /api-keys/{id}               -> revoke
 *
 * API keys are treated as create-once, rename-or-revoke afterwards — the
 * secret itself is never editable or re-fetchable, which matches how most
 * API key systems work. Adjust paths/fields in this file if your backend
 * differs.
 */
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
