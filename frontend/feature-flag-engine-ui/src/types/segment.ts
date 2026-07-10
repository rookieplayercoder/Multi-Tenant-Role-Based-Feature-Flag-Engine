/**
 * Matches {@code SegmentController.SegmentResponse} on the backend exactly.
 * Segments belong to an organization (not a project) and have no inline
 * rules — targeting is done elsewhere via {@code FeatureRule} with a
 * {@code CONDITION} rule pointing at a segment. Segment membership is a
 * separate concept (see {@code SegmentMember}), not part of this shape.
 */
export interface Segment {
  id: string;
  organizationId: string;
  name: string;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
}

/** Matches {@code CreateSegmentRequest} / {@code UpdateSegmentRequest} — name + description only. */
export interface SegmentInput {
  name: string;
  description?: string;
}

/** Matches {@code SegmentController.SegmentMemberResponse}. */
export interface SegmentMember {
  userIdentifier: string;
  addedAt?: string;
}