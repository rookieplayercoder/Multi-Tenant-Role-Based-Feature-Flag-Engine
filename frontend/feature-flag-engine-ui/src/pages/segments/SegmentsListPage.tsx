import { useEffect, useMemo, useState } from 'react';
import { getOrganizations } from '@/api/organizations';
import { Link } from 'react-router-dom';
import { Users, Pencil, Plus, Trash2 } from 'lucide-react';
import { Segment } from '@/types/segment';
import { Organization } from '@/types/organization';
import { deleteSegment, getSegments } from '@/api/segments';
import Card from '@/components/ui/Card';
import Button from '@/components/ui/Button';
import Spinner from '@/components/ui/Spinner';
import ErrorMessage from '@/components/ui/ErrorMessage';
import EmptyState from '@/components/ui/EmptyState';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import Select from '@/components/ui/Select';

export default function SegmentsListPage() {
  const [segments, setSegments] = useState<Segment[]>([]);
  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [organizationFilter, setOrganizationFilter] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<Segment | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  async function loadData() {
    setIsLoading(true);
    setError(null);

    try {
      const orgs = await getOrganizations();
      setOrganizations(orgs);

      let allSegments: Segment[] = [];
      for (const organization of orgs) {
        const orgSegments = await getSegments(organization.id);
        allSegments.push(...orgSegments);
      }
      setSegments(allSegments);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : 'Failed to load segments.'
      );
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  const organizationNameById = useMemo(() => {
    const map = new Map<string, string>();
    organizations.forEach((o) => map.set(o.id, o.name));
    return map;
  }, [organizations]);

  const filteredSegments = useMemo(() => {
    if (!organizationFilter) return segments;
    return segments.filter((s) => s.organizationId === organizationFilter);
  }, [segments, organizationFilter]);

  async function handleDeleteConfirmed() {
    if (!pendingDelete) return;
    setIsDeleting(true);
    try {
      await deleteSegment(pendingDelete.id);
      setSegments((prev) => prev.filter((s) => s.id !== pendingDelete.id));
      setPendingDelete(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete segment.');
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-semibold text-surface-900">Segments</h1>
          <p className="text-sm text-surface-500">
            Group users by identifier so you can target flags at specific
            audiences.
          </p>
        </div>
        <Link to="/segments/new">
          <Button>
            <Plus className="h-4 w-4" />
            New segment
          </Button>
        </Link>
      </div>

      {!isLoading && !error && organizations.length > 0 && (
        <div className="max-w-xs">
          <Select
            aria-label="Filter by organization"
            value={organizationFilter}
            onChange={(e) => setOrganizationFilter(e.target.value)}
          >
            <option value="">All organizations</option>
            {organizations.map((organization) => (
              <option key={organization.id} value={organization.id}>
                {organization.name}
              </option>
            ))}
          </Select>
        </div>
      )}

      {isLoading && (
        <Card className="flex items-center justify-center p-12">
          <Spinner label="Loading segments…" />
        </Card>
      )}

      {!isLoading && error && <ErrorMessage message={error} onRetry={loadData} />}

      {!isLoading && !error && filteredSegments.length === 0 && (
        <EmptyState
          icon={Users}
          title="No segments yet"
          description="Create a segment to target flags at a specific group of users."
          action={
            <Link to="/segments/new">
              <Button variant="secondary">
                <Plus className="h-4 w-4" />
                New segment
              </Button>
            </Link>
          }
        />
      )}

      {!isLoading && !error && filteredSegments.length > 0 && (
        <Card className="overflow-hidden">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-surface-200 bg-surface-50 text-surface-500">
              <tr>
                <th className="px-4 py-3 font-medium">Name</th>
                <th className="hidden px-4 py-3 font-medium sm:table-cell">
                  Description
                </th>
                <th className="hidden px-4 py-3 font-medium sm:table-cell">
                  Organization
                </th>
                <th className="px-4 py-3 font-medium text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-surface-100">
              {filteredSegments.map((segment) => (
                <tr key={segment.id} className="hover:bg-surface-50">
                  <td className="px-4 py-3 font-medium text-surface-900">
                    {segment.name}
                  </td>
                  <td className="hidden px-4 py-3 text-surface-500 sm:table-cell">
                    {segment.description || '—'}
                  </td>
                  <td className="hidden px-4 py-3 text-surface-500 sm:table-cell">
                    {organizationNameById.get(segment.organizationId) || '—'}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-2">
                      <Link
                        to={`/segments/${segment.id}/edit`}
                        className="rounded-md p-1.5 text-surface-500 hover:bg-surface-100 hover:text-surface-900"
                        aria-label={`Edit ${segment.name}`}
                      >
                        <Pencil className="h-4 w-4" />
                      </Link>
                      <button
                        onClick={() => setPendingDelete(segment)}
                        className="rounded-md p-1.5 text-surface-500 hover:bg-red-50 hover:text-red-600"
                        aria-label={`Delete ${segment.name}`}
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      {pendingDelete && (
        <ConfirmDialog
          title={`Delete "${pendingDelete.name}"?`}
          description="This can't be undone. Flags targeting this segment will stop matching it."
          isLoading={isDeleting}
          onConfirm={handleDeleteConfirmed}
          onCancel={() => setPendingDelete(null)}
        />
      )}
    </div>
  );
}
