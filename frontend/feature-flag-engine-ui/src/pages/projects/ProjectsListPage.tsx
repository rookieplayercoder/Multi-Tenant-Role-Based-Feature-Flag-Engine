import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { FolderKanban, Pencil, Plus, Trash2 } from 'lucide-react';
import { Project } from '@/types/project';
import { Organization } from '@/types/organization';
import { deleteProject, getProjects } from '@/api/projects';
import { getOrganizations } from '@/api/organizations';
import Card from '@/components/ui/Card';
import Button from '@/components/ui/Button';
import Spinner from '@/components/ui/Spinner';
import ErrorMessage from '@/components/ui/ErrorMessage';
import EmptyState from '@/components/ui/EmptyState';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import Select from '@/components/ui/Select';

export default function ProjectsListPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [organizationFilter, setOrganizationFilter] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<Project | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  async function loadData() {
    setIsLoading(true);
    setError(null);
    try {
      const [projectsData, orgsData] = await Promise.all([
        getProjects(),
        getOrganizations(),
      ]);
      setProjects(projectsData);
      setOrganizations(orgsData);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load projects.');
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  const organizationNameById = useMemo(() => {
    const map = new Map<string, string>();
    organizations.forEach((org) => map.set(org.id, org.name));
    return map;
  }, [organizations]);

  const filteredProjects = useMemo(() => {
    if (!organizationFilter) return projects;
    return projects.filter((p) => p.organizationId === organizationFilter);
  }, [projects, organizationFilter]);

  async function handleDeleteConfirmed() {
    if (!pendingDelete) return;
    setIsDeleting(true);
    try {
      await deleteProject(pendingDelete.id);
      setProjects((prev) => prev.filter((p) => p.id !== pendingDelete.id));
      setPendingDelete(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete project.');
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-semibold text-surface-900">Projects</h1>
          <p className="text-sm text-surface-500">
            Projects group environments and feature flags under an organization.
          </p>
        </div>
        <Link to="/projects/new">
          <Button>
            <Plus className="h-4 w-4" />
            New project
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
            {organizations.map((org) => (
              <option key={org.id} value={org.id}>
                {org.name}
              </option>
            ))}
          </Select>
        </div>
      )}

      {isLoading && (
        <Card className="flex items-center justify-center p-12">
          <Spinner label="Loading projects…" />
        </Card>
      )}

      {!isLoading && error && (
        <ErrorMessage message={error} onRetry={loadData} />
      )}

      {!isLoading && !error && filteredProjects.length === 0 && (
        <EmptyState
          icon={FolderKanban}
          title="No projects yet"
          description="Create a project to start configuring environments and flags."
          action={
            <Link to="/projects/new">
              <Button variant="secondary">
                <Plus className="h-4 w-4" />
                New project
              </Button>
            </Link>
          }
        />
      )}

      {!isLoading && !error && filteredProjects.length > 0 && (
        <Card className="overflow-hidden">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-surface-200 bg-surface-50 text-surface-500">
              <tr>
                <th className="px-4 py-3 font-medium">Name</th>
                <th className="hidden px-4 py-3 font-medium sm:table-cell">Key</th>
                <th className="px-4 py-3 font-medium">Organization</th>
                <th className="px-4 py-3 font-medium text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-surface-100">
              {filteredProjects.map((project) => (
                <tr key={project.id} className="hover:bg-surface-50">
                  <td className="px-4 py-3 font-medium text-surface-900">
                    {project.name}
                  </td>
                  <td className="hidden px-4 py-3 text-surface-500 sm:table-cell">
                    {project.key || '—'}
                  </td>
                  <td className="px-4 py-3 text-surface-500">
                    {project.organizationName ||
                      organizationNameById.get(project.organizationId) ||
                      '—'}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-2">
                      <Link
                        to={`/projects/${project.id}/edit`}
                        className="rounded-md p-1.5 text-surface-500 hover:bg-surface-100 hover:text-surface-900"
                        aria-label={`Edit ${project.name}`}
                      >
                        <Pencil className="h-4 w-4" />
                      </Link>
                      <button
                        onClick={() => setPendingDelete(project)}
                        className="rounded-md p-1.5 text-surface-500 hover:bg-red-50 hover:text-red-600"
                        aria-label={`Delete ${project.name}`}
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
          description="This can't be undone. Environments and flags under this project may be affected."
          isLoading={isDeleting}
          onConfirm={handleDeleteConfirmed}
          onCancel={() => setPendingDelete(null)}
        />
      )}
    </div>
  );
}
