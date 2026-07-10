/** Matches the backend's fixed {@code EnvironmentType} enum exactly. */
export type EnvironmentKey = 'DEV' | 'TEST' | 'STAGING' | 'PROD';

export interface Environment {
  id: string;
  name: string;
  key: EnvironmentKey;
  description?: string;
  projectId: string;
  projectName?: string;
  createdAt?: string;
}

/** Matches {@code CreateEnvironmentRequest} — key is required, one of the fixed enum values. */
export interface EnvironmentInput {
  name: string;
  key: EnvironmentKey;
  description?: string;
  projectId: string;
}