import apiClient from './client';
import { Segment, SegmentInput } from '@/types/segment';

/**
 * Assumes a flat /segments resource where each segment carries a projectId
 * and an inline `rules` array, using standard REST conventions:
 * GET/POST /segments, GET/PUT/DELETE /segments/{id}.
 * If your API stores rules as a separate sub-resource instead of inline,
 * update this file — the UI just reads/writes `segment.rules`.
 */
export async function getSegments(projectId?: string): Promise<Segment[]> {
  const { data } = await apiClient.get<Segment[]>('/segments', {
    params: projectId ? { projectId } : undefined,
  });
  return data;
}

export async function getSegment(id: string): Promise<Segment> {
  const { data } = await apiClient.get<Segment>(`/segments/${id}`);
  return data;
}

export async function createSegment(payload: SegmentInput): Promise<Segment> {
  const { data } = await apiClient.post<Segment>('/segments', payload);
  return data;
}

export async function updateSegment(
  id: string,
  payload: SegmentInput
): Promise<Segment> {
  const { data } = await apiClient.put<Segment>(`/segments/${id}`, payload);
  return data;
}

export async function deleteSegment(id: string): Promise<void> {
  await apiClient.delete(`/segments/${id}`);
}
