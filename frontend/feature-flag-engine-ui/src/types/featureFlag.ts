export type FlagType = 'boolean' | 'string' | 'number' | 'json';

export interface FeatureFlag {
  id: string;
  key: string;
  name: string;
  description?: string;
  type: FlagType;
  projectId: string;
  projectName?: string;
  createdAt?: string;
}

export interface FeatureFlagInput {
  key: string;
  name: string;
  description?: string;
  type: FlagType;
  projectId: string;
}

export interface FlagEnvironmentState {
  environmentId: string;
  environmentName?: string;
  enabled: boolean;
}
