import apiClient from './client';
import { Segment, SegmentInput, SegmentMember } from '@/types/segment';

export async function getSegments(
  organizationId: string
): Promise<Segment[]> {
  const { data } = await apiClient.get<Segment[]>(
    `/organizations/${organizationId}/segments`
  );
  return data;
}

export async function getSegment(id: string): Promise<Segment> {
  const { data } = await apiClient.get<Segment>(`/segments/${id}`);
  return data;
}

export async function createSegment(
  organizationId: string,
  payload: SegmentInput
): Promise<Segment> {
  const { data } = await apiClient.post<Segment>(
    `/organizations/${organizationId}/segments`,
    payload
  );
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

export async function getSegmentMembers(
  segmentId: string
): Promise<SegmentMember[]> {
  const { data } = await apiClient.get<SegmentMember[]>(
    `/segments/${segmentId}/members`
  );
  return data;
}

export async function addSegmentMember(
  segmentId: string,
  userIdentifier: string
): Promise<SegmentMember> {
  const { data } = await apiClient.post<SegmentMember>(
    `/segments/${segmentId}/members`,
    { userIdentifier }
  );
  return data;
}

export async function removeSegmentMember(
  segmentId: string,
  userIdentifier: string
): Promise<void> {
  await apiClient.delete(`/segments/${segmentId}/members`, {
    params: { userIdentifier },
  });
}