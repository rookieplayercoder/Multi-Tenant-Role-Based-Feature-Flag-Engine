import apiClient from './client';
import { AuditLogFilters, AuditLogPage } from '@/types/auditLog';


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
