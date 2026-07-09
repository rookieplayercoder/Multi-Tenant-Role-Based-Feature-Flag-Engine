import { useEffect, useMemo, useState } from 'react';
import { Environment } from "@/types/environment";
import { getOrganizations } from "@/api/organizations";
import { getEnvironments } from "@/api/environments";
import { Link } from 'react-router-dom';
import { Flag, Pencil, Plus, Trash2 } from 'lucide-react';
import { FeatureFlag } from '@/types/featureFlag';
import { Project } from '@/types/project';
import { deleteFeatureFlag, getFeatureFlags } from '@/api/featureFlags';
import { getProjects } from '@/api/projects';
import Card from '@/components/ui/Card';
import Button from '@/components/ui/Button';
import Spinner from '@/components/ui/Spinner';
import ErrorMessage from '@/components/ui/ErrorMessage';
import EmptyState from '@/components/ui/EmptyState';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import Select from '@/components/ui/Select';
import Badge from '@/components/ui/Badge';

export default function FeatureFlagsListPage() {
  const [flags, setFlags] = useState<FeatureFlag[]>([]);
  const [projects, setProjects] = useState<Project[]>([]);
  const [projectFilter, setProjectFilter] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<FeatureFlag | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  async function loadData() {
    setIsLoading(true);
    setError(null);

    try {
      const organizations = await getOrganizations();

      let allProjects: Project[] = [];

      for (const organization of organizations) {
        const projects = await getProjects(organization.id);
        allProjects.push(...projects);
      }

      let allEnvironments: Environment[] = [];

      for (const project of allProjects) {
        const environments = await getEnvironments(project.id);
        allEnvironments.push(...environments);
      }

      let allFlags: FeatureFlag[] = [];

      for (const environment of allEnvironments) {
        const flags = await getFeatureFlags(environment.id);
        allFlags.push(...flags);
      }

      setProjects(allProjects);
      setFlags(allFlags);

    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Failed to load feature flags."
      );
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

  const filteredFlags = useMemo(() => {
    if (!projectFilter) return flags;
    return flags.filter((f) => f.projectId === projectFilter);
  }, [flags, projectFilter]);

  async function handleDeleteConfirmed() {
    if (!pendingDelete) return;
    setIsDeleting(true);
    try {
      await deleteFeatureFlag(pendingDelete.id);
      setFlags((prev) => prev.filter((f) => f.id !== pendingDelete.id));
      setPendingDelete(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete feature flag.');
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-semibold text-surface-900">Feature Flags</h1>
          <p className="text-sm text-surface-500">
            Manage flags and toggle them per environment.
          </p>
        </div>
        <Link to="/flags/new">
          <Button>
            <Plus className="h-4 w-4" />
            New flag
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
          <Spinner label="Loading feature flags…" />
        </Card>
      )}

      {!isLoading && error && <ErrorMessage message={error} onRetry={loadData} />}

      {!isLoading && !error && filteredFlags.length === 0 && (
        <EmptyState
          icon={Flag}
          title="No feature flags yet"
          description="Create a flag to start rolling out features safely."
          action={
            <Link to="/flags/new">
              <Button variant="secondary">
                <Plus className="h-4 w-4" />
                New flag
              </Button>
            </Link>
          }
        />
      )}

      {!isLoading && !error && filteredFlags.length > 0 && (
        <Card className="overflow-hidden">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-surface-200 bg-surface-50 text-surface-500">
              <tr>
                <th className="px-4 py-3 font-medium">Key</th>
                <th className="hidden px-4 py-3 font-medium sm:table-cell">Name</th>
                <th className="hidden px-4 py-3 font-medium md:table-cell">Type</th>
                <th className="px-4 py-3 font-medium">Project</th>
                <th className="px-4 py-3 font-medium text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-surface-100">
              {filteredFlags.map((flag) => (
                <tr key={flag.id} className="hover:bg-surface-50">
                  <td className="px-4 py-3 font-mono text-xs text-surface-900">
                    {flag.key}
                  </td>
                  <td className="hidden px-4 py-3 text-surface-500 sm:table-cell">
                    {flag.name}
                  </td>
                  <td className="hidden px-4 py-3 md:table-cell">
                    <Badge tone="brand">{flag.type}</Badge>
                  </td>
                  <td className="px-4 py-3 text-surface-500">
                    {flag.projectName || projectNameById.get(flag.projectId) || '—'}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-2">
                      <Link
                        to={`/flags/${flag.id}/edit`}
                        className="rounded-md p-1.5 text-surface-500 hover:bg-surface-100 hover:text-surface-900"
                        aria-label={`Edit ${flag.name}`}
                      >
                        <Pencil className="h-4 w-4" />
                      </Link>
                      <button
                        onClick={() => setPendingDelete(flag)}
                        className="rounded-md p-1.5 text-surface-500 hover:bg-red-50 hover:text-red-600"
                        aria-label={`Delete ${flag.name}`}
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
          description="This can't be undone. All environment overrides for this flag will be lost."
          isLoading={isDeleting}
          onConfirm={handleDeleteConfirmed}
          onCancel={() => setPendingDelete(null)}
        />
      )}
    </div>
  );
}
