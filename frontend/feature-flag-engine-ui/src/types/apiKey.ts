export type ApiKeyType = 'server' | 'client';

export interface ApiKey {
  id: string;
  name: string;
  /** Masked form for display in lists, e.g. "ffe_live_••••ab12". Never the full secret. */
  maskedKey?: string;
  /**
   * Full secret value. Only ever present in the response right after
   * creation — the backend should never return it again afterwards.
   */
  key?: string;
  type: ApiKeyType;
  environmentId: string;
  environmentName?: string;
  createdAt?: string;
  lastUsedAt?: string;
}

export interface ApiKeyInput {
  name: string;
  type: ApiKeyType;
  environmentId: string;
}
