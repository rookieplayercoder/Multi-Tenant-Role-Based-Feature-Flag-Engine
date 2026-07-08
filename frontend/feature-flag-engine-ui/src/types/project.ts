export interface Project {
  id: string;
  name: string;
  key?: string;
  description?: string;
  organizationId: string;
  organizationName?: string;
  createdAt?: string;
}

export interface ProjectInput {
  name: string;
  key?: string;
  description?: string;
  organizationId: string;
}
