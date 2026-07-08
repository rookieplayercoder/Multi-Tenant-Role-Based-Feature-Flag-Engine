import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Layers, Pencil, Plus, Trash2 } from 'lucide-react';
import { Environment } from '@/types/environment';
import { Project } from '@/types/project';
import { deleteEnvironment, getEnvironments } from '@/api/environments';
import { getProjects } from '@/api/projects';
import Card from '@/components/ui/Card';
import Button from '@/components/ui/Button';
import Spinner from '@/components/ui/Spinner';
import ErrorMessage from '@/components/ui/ErrorMessage';
import EmptyState from '@/components/ui/EmptyState';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import Select from '@/components/ui/Select';

export default function EnvironmentsListPage() {
  const [environments, setEnvironments] = useState<Environment[]>([]);
  const [projects, setProjects] = useState<Project[]>([]);
  const [projectFilter, setProjectFilter] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<Environment | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  async function loadData() {
    setIsLoading(true);
    setError(null);
    try {
      const [environmentsData, projectsData] = await Promise.all([
        getEnvironments(),
        getProjects(),
      ]);
      setEnvironments(environmentsData);
      setProjects(projectsData);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load environments.');
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  const projectNameById = useMemo(() => {
    const map = new Map<string, string>();
    projects.forEach((p) => map.set(p.id, p.name));
    return map;
  }, [projects]);

  const filteredEnvironments = useMemo(() => {
    if (!projectFilter) return environments;
    return environments.filter((e) => e.projectId === projectFilter);
  }, [environments, projectFilter]);

  async function handleDeleteConfirmed() {
    if (!pendingDelete) return;
    setIsDeleting(true);
    try {
      await deleteEnvironment(pendingDelete.id);
      setEnvironments((prev) => prev.filter((e) => e.id !== pendingDelete.id));
      setPendingDelete(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete environment.');
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-semibold text-surface-900">Environments</h1>
          <p className="text-sm text-surface-500">
            Environments (e.g. development, staging, production) scope how flags
            behave within a project.
          </p>
        </div>
        <Link to="/environments/new">
          <Button>
            <Plus className="h-4 w-4" />
            New environment
          </Button>
        </Link>
      </div>

      {!isLoading && !error && projects.length > 0 && (
        <div className="max-w-xs">
          <Select
            aria-label="Filter by project"
            value={projectFilter}
            onChange={(e) => setProjectFilter(e.target.value)}
          >
            <option value="">All projects</option>
            {projects.map((project) => (
              <option key={project.id} value={project.id}>
                {project.name}
              </option>
            ))}
          </Select>
        </div>
      )}

      {isLoading && (
        <Card className="flex items-center justify-center p-12">
          <Spinner label="Loading environments…" />
        </Card>
      )}

      {!isLoading && error && (
        <ErrorMessage message={error} onRetry={loadData} />
      )}

      {!isLoading && !error && filteredEnvironments.length === 0 && (
        <EmptyState
          icon={Layers}
          title="No environments yet"
          description="Create an environment to start scoping flags for a project."
          action={
            <Link to="/environments/new">
              <Button variant="secondary">
                <Plus className="h-4 w-4" />
                New environment
              </Button>
            </Link>
          }
        />
      )}

      {!isLoading && !error && filteredEnvironments.length > 0 && (
        <Card className="overflow-hidden">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-surface-200 bg-surface-50 text-surface-500">
              <tr>
                <th className="px-4 py-3 font-medium">Name</th>
                <th className="hidden px-4 py-3 font-medium sm:table-cell">Key</th>
                <th className="px-4 py-3 font-medium">Project</th>
                <th className="px-4 py-3 font-medium text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-surface-100">
              {filteredEnvironments.map((env) => (
                <tr key={env.id} className="hover:bg-surface-50">
                  <td className="px-4 py-3 font-medium text-surface-900">
                    {env.name}
                  </td>
                  <td className="hidden px-4 py-3 text-surface-500 sm:table-cell">
                    {env.key || '—'}
                  </td>
                  <td className="px-4 py-3 text-surface-500">
                    {env.projectName || projectNameById.get(env.projectId) || '—'}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-2">
                      <Link
                        to={`/environments/${env.id}/edit`}
                        className="rounded-md p-1.5 text-surface-500 hover:bg-surface-100 hover:text-surface-900"
                        aria-label={`Edit ${env.name}`}
                      >
                        <Pencil className="h-4 w-4" />
                      </Link>
                      <button
                        onClick={() => setPendingDelete(env)}
                        className="rounded-md p-1.5 text-surface-500 hover:bg-red-50 hover:text-red-600"
                        aria-label={`Delete ${env.name}`}
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
          description="This can't be undone. Feature flag values configured for this environment will be lost."
          isLoading={isDeleting}
          onConfirm={handleDeleteConfirmed}
          onCancel={() => setPendingDelete(null)}
        />
      )}
    </div>
  );
}
