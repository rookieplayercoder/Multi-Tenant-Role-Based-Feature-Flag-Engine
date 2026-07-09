import { useEffect, useMemo, useState } from 'react';
import { ChevronLeft, ChevronRight, ScrollText } from 'lucide-react';
import { AuditAction, AuditLogEntry } from '@/types/auditLog';
import { Organization } from '@/types/organization';
import { getOrganizations } from '@/api/organizations';
import { getAuditLogs } from '@/api/auditLogs';
import Card from '@/components/ui/Card';
import Button from '@/components/ui/Button';
import Spinner from '@/components/ui/Spinner';
import ErrorMessage from '@/components/ui/ErrorMessage';
import EmptyState from '@/components/ui/EmptyState';
import Select from '@/components/ui/Select';
import Badge from '@/components/ui/Badge';

const ENTITY_TYPES = [
  'organization',
  'project',
  'environment',
  'flag',
  'segment',
  'api_key',
];

const ACTIONS: AuditAction[] = ['created', 'updated', 'deleted', 'enabled', 'disabled'];

const actionTone: Record<AuditAction, 'brand' | 'green' | 'red' | 'neutral'> = {
  created: 'brand',
  updated: 'neutral',
  deleted: 'red',
  enabled: 'green',
  disabled: 'red',
};

const PAGE_SIZE = 20;

export default function AuditLogsListPage() {
  const [entries, setEntries] = useState<AuditLogEntry[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [organizationId, setOrganizationId] = useState("");


  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function loadLogs() {
    setIsLoading(true);
    setError(null);
    try {
      if (!organizationId) return;

      const result = await getAuditLogs(organizationId, page - 1);
      setEntries(result.content);
      setTotal(result.totalElements);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load audit logs.');
    } finally {
      setIsLoading(false);
    }
  }

useEffect(() => {
  async function loadOrganizations() {
    try {
      const data = await getOrganizations();
      setOrganizations(data);
    } catch {
      setOrganizations([]);
    }
  }

  loadOrganizations();
}, []);

 useEffect(() => {
     if (organizationId) {
         loadLogs();
     }
 }, [organizationId, page]);

  function handleFilterChange(setter: (v: string) => void) {
    return (value: string) => {
      setPage(1);
      setter(value);
    };
  }

  const totalPages = useMemo(
    () => Math.max(1, Math.ceil(total / PAGE_SIZE)),
    [total]
  );

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold text-surface-900">Audit Logs</h1>
        <p className="text-sm text-surface-500">
          A read-only history of changes made across your organizations.
        </p>
      </div>

      <div className="max-w-xs">
        <Select
          aria-label="Select organization"
          value={organizationId}
          onChange={(e) => {
            setPage(1);
            setOrganizationId(e.target.value);
          }}
        >
          <option value="">Select Organization</option>

          {organizations.map((organization) => (
            <option key={organization.id} value={organization.id}>
              {organization.name}
            </option>
          ))}
        </Select>
      </div>

      {isLoading && (
        <Card className="flex items-center justify-center p-12">
          <Spinner label="Loading audit logs…" />
        </Card>
      )}

      {!isLoading && error && <ErrorMessage message={error} onRetry={loadLogs} />}

      {!isLoading && !error && entries.length === 0 && (
        <EmptyState
          icon={ScrollText}
          title="No audit log entries"
          description="Changes made across your organizations will show up here."
        />
      )}

      {!isLoading && !error && entries.length > 0 && (
        <>
          <Card className="overflow-hidden">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-surface-200 bg-surface-50 text-surface-500">
                <tr>
                  <th className="px-4 py-3 font-medium">When</th>
                  <th className="px-4 py-3 font-medium">Action</th>
                  <th className="px-4 py-3 font-medium">Entity</th>
                  <th className="hidden px-4 py-3 font-medium sm:table-cell">
                    Actor
                  </th>
                  <th className="hidden px-4 py-3 font-medium md:table-cell">
                    Project
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-surface-100">
                {entries.map((entry) => (
                  <tr key={entry.id} className="hover:bg-surface-50">
                    <td className="whitespace-nowrap px-4 py-3 text-surface-500">
                      {new Date(entry.timestamp).toLocaleString()}
                    </td>
                    <td className="px-4 py-3">
                      <Badge tone={actionTone[entry.action] ?? 'neutral'}>
                        {entry.action}
                      </Badge>
                    </td>
                    <td className="px-4 py-3 text-surface-900">
                      <span className="font-medium">
                        {entry.entityName || entry.entityId || '—'}
                      </span>
                      <span className="ml-1.5 text-xs text-surface-400">
                        ({entry.entityType.replace('_', ' ')})
                      </span>
                    </td>
                    <td className="hidden px-4 py-3 text-surface-500 sm:table-cell">
                      {entry.actorName || entry.actorEmail || 'System'}
                    </td>
                    <td className="hidden px-4 py-3 text-surface-500 md:table-cell">
                      {entry.projectName || '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>

          <div className="flex items-center justify-between">
            <p className="text-sm text-surface-500">
              Page {page} of {totalPages} · {total} total
            </p>
            <div className="flex gap-2">
              <Button
                variant="secondary"
                onClick={() => setPage((p) => Math.max(1, p - 1))}
                disabled={page <= 1}
              >
                <ChevronLeft className="h-4 w-4" />
                Previous
              </Button>
              <Button
                variant="secondary"
                onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                disabled={page >= totalPages}
              >
                Next
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
