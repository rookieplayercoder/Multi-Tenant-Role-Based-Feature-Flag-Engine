import apiClient from './client';
import { AuditLogFilters, AuditLogPage } from '@/types/auditLog';

/**
 * Assumes a read-only, paginated /audit-logs endpoint:
 *   GET /audit-logs?projectId=&entityType=&action=&page=&pageSize=
 *     -> { items: AuditLogEntry[], total, page, pageSize }
 *
 * If your API returns a plain array instead of a paginated envelope,
 * or uses different filter/query param names, this is the only file
 * to change — the page itself just calls getAuditLogs().
 */
export async function getAuditLogs(
  organizationId: string,
  page = 0,
  size = 20
) {
  const { data } = await apiClient.get(
    `/organizations/${organizationId}/audit-logs`,
    {
      params: {
        page,
        size,
      },
    }
  );

  return data;
}