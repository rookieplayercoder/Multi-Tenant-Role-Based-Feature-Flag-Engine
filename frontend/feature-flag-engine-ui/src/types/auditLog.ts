export type AuditAction = 'created' | 'updated' | 'deleted' | 'enabled' | 'disabled';

export interface AuditLogEntry {
  id: string;
  action: AuditAction;
  entityType: string;
  entityId?: string;
  entityName?: string;
  actorName?: string;
  actorEmail?: string;
  projectId?: string;
  projectName?: string;
  timestamp: string;
  details?: string;
}

export interface AuditLogFilters {
  projectId?: string;
  entityType?: string;
  action?: AuditAction;
  page?: number;
  pageSize?: number;
}

export interface AuditLogPage {
  items: AuditLogEntry[];
  total: number;
  page: number;
  pageSize: number;
}
