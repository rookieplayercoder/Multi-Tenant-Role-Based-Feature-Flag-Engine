import apiClient from './client';
import {
  FeatureFlag,
  FeatureFlagInput,
  FlagEnvironmentState,
} from '@/types/featureFlag';

/**
 * Assumes a flat /flags resource where each flag carries a projectId, plus a
 * sub-resource for per-environment enabled state:
 *   GET    /flags?projectId=...
 *   GET    /flags/{id}
 *   POST   /flags
 *   PUT    /flags/{id}
 *   DELETE /flags/{id}
 *   GET    /flags/{id}/environments        -> FlagEnvironmentState[]
 *   PUT    /flags/{id}/environments/{envId} -> { enabled }
 *
 * This last part (per-environment toggles) is the part we're least sure
 * matches your backend exactly — adjust freely, the rest of this file and
 * the UI don't depend on its specifics.
 */
export async function getFeatureFlags(projectId?: string): Promise<FeatureFlag[]> {
  const { data } = await apiClient.get<FeatureFlag[]>('/flags', {
    params: projectId ? { projectId } : undefined,
  });
  return data;
}

export async function getFeatureFlag(id: string): Promise<FeatureFlag> {
  const { data } = await apiClient.get<FeatureFlag>(`/flags/${id}`);
  return data;
}

export async function createFeatureFlag(
  payload: FeatureFlagInput
): Promise<FeatureFlag> {
  const { data } = await apiClient.post<FeatureFlag>('/flags', payload);
  return data;
}

export async function updateFeatureFlag(
  id: string,
  payload: FeatureFlagInput
): Promise<FeatureFlag> {
  const { data } = await apiClient.put<FeatureFlag>(`/flags/${id}`, payload);
  return data;
}

export async function deleteFeatureFlag(id: string): Promise<void> {
  await apiClient.delete(`/flags/${id}`);
}

export async function getFlagEnvironmentStates(
  flagId: string
): Promise<FlagEnvironmentState[]> {
  const { data } = await apiClient.get<FlagEnvironmentState[]>(
    `/flags/${flagId}/environments`
  );
  return data;
}

export async function setFlagEnvironmentState(
  flagId: string,
  environmentId: string,
  enabled: boolean
): Promise<FlagEnvironmentState> {
  const { data } = await apiClient.put<FlagEnvironmentState>(
    `/flags/${flagId}/environments/${environmentId}`,
    { enabled }
  );
  return data;
}
