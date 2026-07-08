export interface Organization {
  id: string;
  name: string;
  slug?: string;
  description?: string;
  createdAt?: string;
}

export interface OrganizationInput {
  name: string;
  slug?: string;
  description?: string;
}
