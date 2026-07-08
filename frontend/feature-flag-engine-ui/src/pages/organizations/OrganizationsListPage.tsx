import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Building2, Pencil, Plus, Trash2 } from 'lucide-react';
import { Organization } from '@/types/organization';
import { deleteOrganization, getOrganizations } from '@/api/organizations';
import Card from '@/components/ui/Card';
import Button from '@/components/ui/Button';
import Spinner from '@/components/ui/Spinner';
import ErrorMessage from '@/components/ui/ErrorMessage';
import EmptyState from '@/components/ui/EmptyState';
import ConfirmDialog from '@/components/ui/ConfirmDialog';

export default function OrganizationsListPage() {
  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<Organization | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  async function loadOrganizations() {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getOrganizations();
      setOrganizations(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load organizations.');
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    loadOrganizations();
  }, []);

  async function handleDeleteConfirmed() {
    if (!pendingDelete) return;
    setIsDeleting(true);
    try {
      await deleteOrganization(pendingDelete.id);
      setOrganizations((prev) => prev.filter((o) => o.id !== pendingDelete.id));
      setPendingDelete(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete organization.');
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-surface-900">Organizations</h1>
          <p className="text-sm text-surface-500">
            Manage the organizations in your Feature Flag Engine.
          </p>
        </div>
        <Link to="/organizations/new">
          <Button>
            <Plus className="h-4 w-4" />
            New organization
          </Button>
        </Link>
      </div>

      {isLoading && (
        <Card className="flex items-center justify-center p-12">
          <Spinner label="Loading organizations…" />
        </Card>
      )}

      {!isLoading && error && (
        <ErrorMessage message={error} onRetry={loadOrganizations} />
      )}

      {!isLoading && !error && organizations.length === 0 && (
        <EmptyState
          icon={Building2}
          title="No organizations yet"
          description="Create your first organization to start grouping projects."
          action={
            <Link to="/organizations/new">
              <Button variant="secondary">
                <Plus className="h-4 w-4" />
                New organization
              </Button>
            </Link>
          }
        />
      )}

      {!isLoading && !error && organizations.length > 0 && (
        <Card className="overflow-hidden">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-surface-200 bg-surface-50 text-surface-500">
              <tr>
                <th className="px-4 py-3 font-medium">Name</th>
                <th className="hidden px-4 py-3 font-medium sm:table-cell">Slug</th>
                <th className="hidden px-4 py-3 font-medium md:table-cell">
                  Description
                </th>
                <th className="px-4 py-3 font-medium text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-surface-100">
              {organizations.map((org) => (
                <tr key={org.id} className="hover:bg-surface-50">
                  <td className="px-4 py-3 font-medium text-surface-900">
                    {org.name}
                  </td>
                  <td className="hidden px-4 py-3 text-surface-500 sm:table-cell">
                    {org.slug || '—'}
                  </td>
                  <td className="hidden max-w-xs truncate px-4 py-3 text-surface-500 md:table-cell">
                    {org.description || '—'}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-2">
                      <Link
                        to={`/organizations/${org.id}/edit`}
                        className="rounded-md p-1.5 text-surface-500 hover:bg-surface-100 hover:text-surface-900"
                        aria-label={`Edit ${org.name}`}
                      >
                        <Pencil className="h-4 w-4" />
                      </Link>
                      <button
                        onClick={() => setPendingDelete(org)}
                        className="rounded-md p-1.5 text-surface-500 hover:bg-red-50 hover:text-red-600"
                        aria-label={`Delete ${org.name}`}
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
          description="This can't be undone. Projects under this organization may be affected."
          isLoading={isDeleting}
          onConfirm={handleDeleteConfirmed}
          onCancel={() => setPendingDelete(null)}
        />
      )}
    </div>
  );
}
