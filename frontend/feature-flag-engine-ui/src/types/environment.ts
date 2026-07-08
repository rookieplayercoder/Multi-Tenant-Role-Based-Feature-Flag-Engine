export interface Environment {
  id: string;
  name: string;
  key?: string;
  description?: string;
  projectId: string;
  projectName?: string;
  createdAt?: string;
}

export interface EnvironmentInput {
  name: string;
  key?: string;
  description?: string;
  projectId: string;
}
