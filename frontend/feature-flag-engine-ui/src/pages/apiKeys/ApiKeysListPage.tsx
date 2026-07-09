import { Organization } from "@/types/organization";
import { Project } from "@/types/project";

import { getOrganizations } from "@/api/organizations";
import { getProjects } from "@/api/projects";
import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { KeyRound, Plus, Trash2 } from 'lucide-react';
import { ApiKey } from '@/types/apiKey';
import { Environment } from '@/types/environment';
import { deleteApiKey, getApiKeys } from '@/api/apiKeys';
import { getEnvironments } from '@/api/environments';
import Card from '@/components/ui/Card';
import Button from '@/components/ui/Button';
import Spinner from '@/components/ui/Spinner';
import ErrorMessage from '@/components/ui/ErrorMessage';
import EmptyState from '@/components/ui/EmptyState';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import Select from '@/components/ui/Select';
import Badge from '@/components/ui/Badge';

export default function ApiKeysListPage() {
  const [apiKeys, setApiKeys] = useState<ApiKey[]>([]);
  const [environments, setEnvironments] = useState<Environment[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<ApiKey | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [projects, setProjects] = useState<Project[]>([]);

  const [organizationId, setOrganizationId] = useState("");
  const [projectId, setProjectId] = useState("");
  const [environmentId, setEnvironmentId] = useState("");

  async function loadOrganizations() {
      setIsLoading(true);
      setError(null);

      try {
          const data = await getOrganizations();
          setOrganizations(data);
          if (data.length > 0) {
              setOrganizationId(data[0].id);
          }
      } catch (err) {
          setError(err instanceof Error ? err.message : "Failed to load organizations.");
      } finally {
          setIsLoading(false);
      }
  }

useEffect(() => {
    loadOrganizations();
}, []);

useEffect(() => {
    if (!organizationId) return;

    async function loadProjects() {
        const data = await getProjects(organizationId);
        setProjects(data);
        if (data.length > 0) {
            setProjectId(data[0].id);
        }
    }

    loadProjects();
}, [organizationId]);

useEffect(() => {
    if (!projectId) return;

    async function loadEnvironments() {
        const data = await getEnvironments(projectId);
        setEnvironments(data);
        if (data.length > 0) {
            setEnvironmentId(data[0].id);
        }
    }

    loadEnvironments();
}, [projectId]);

useEffect(() => {
    if (!environmentId) return;

    async function loadApiKeys() {
        setIsLoading(true);

        try {
            const data = await getApiKeys(environmentId);
            setApiKeys(data);
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to load API Keys.");
        } finally {
            setIsLoading(false);
        }
    }

    loadApiKeys();
}, [environmentId]);

  const environmentNameById = useMemo(() => {
    const map = new Map<string, string>();
    environments.forEach((e) => map.set(e.id, e.name));
    return map;
  }, [environments]);

  async function handleDeleteConfirmed() {
    if (!pendingDelete) return;
    setIsDeleting(true);
    try {
      await deleteApiKey(environmentId, pendingDelete.id);
      setApiKeys((prev) => prev.filter((k) => k.id !== pendingDelete.id));
      setPendingDelete(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to revoke API key.');
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-semibold text-surface-900">API Keys</h1>
          <p className="text-sm text-surface-500">
            Keys let your apps and SDKs read flags for a specific environment.
          </p>
        </div>
        <Link to="/api-keys/new">
          <Button>
            <Plus className="h-4 w-4" />
            New API key
          </Button>
        </Link>
      </div>

      {!isLoading && !error && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">

          <Select
            value={organizationId}
            onChange={(e) => setOrganizationId(e.target.value)}
          >
            <option value="">Select Organization</option>
            {organizations.map((org) => (
              <option key={org.id} value={org.id}>
                {org.name}
              </option>
            ))}
          </Select>

          <Select
            value={projectId}
            onChange={(e) => setProjectId(e.target.value)}
          >
            <option value="">Select Project</option>
            {projects.map((project) => (
              <option key={project.id} value={project.id}>
                {project.name}
              </option>
            ))}
          </Select>

          <Select
            value={environmentId}
            onChange={(e) => setEnvironmentId(e.target.value)}
          >
            <option value="">Select Environment</option>
            {environments.map((env) => (
              <option key={env.id} value={env.id}>
                {env.name}
              </option>
            ))}
          </Select>

        </div>
      )}

      {isLoading && (
        <Card className="flex items-center justify-center p-12">
          <Spinner label="Loading API keys…" />
        </Card>
      )}

      {!isLoading && error && <ErrorMessage message={error} onRetry={loadOrganizations} />}

      {!isLoading && !error && apiKeys.length === 0 && (
        <EmptyState
          icon={KeyRound}
          title="No API keys yet"
          description="Create a key so your SDKs can fetch flags for an environment."
          action={
            <Link to="/api-keys/new">
              <Button variant="secondary">
                <Plus className="h-4 w-4" />
                New API key
              </Button>
            </Link>
          }
        />
      )}

      {!isLoading && !error && apiKeys.length > 0 && (
        <Card className="overflow-hidden">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-surface-200 bg-surface-50 text-surface-500">
              <tr>
                <th className="px-4 py-3 font-medium">Name</th>
                <th className="px-4 py-3 font-medium">Key</th>
                <th className="hidden px-4 py-3 font-medium sm:table-cell">Type</th>
                <th className="hidden px-4 py-3 font-medium md:table-cell">
                  Environment
                </th>
                <th className="hidden px-4 py-3 font-medium lg:table-cell">
                  Last used
                </th>
                <th className="px-4 py-3 font-medium text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-surface-100">
              {apiKeys.map((apiKey) => (
                <tr key={apiKey.id} className="hover:bg-surface-50">
                  <td className="px-4 py-3 font-medium text-surface-900">
                    {apiKey.name}
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-surface-500">
                    {apiKey.maskedKey || '••••••••'}
                  </td>
                  <td className="hidden px-4 py-3 sm:table-cell">
                    <Badge tone={apiKey.type === 'server' ? 'brand' : 'neutral'}>
                      {apiKey.type}
                    </Badge>
                  </td>
                  <td className="hidden px-4 py-3 text-surface-500 md:table-cell">
                    {apiKey.environmentName ||
                      environmentNameById.get(apiKey.environmentId) ||
                      '—'}
                  </td>
                  <td className="hidden px-4 py-3 text-surface-500 lg:table-cell">
                    {apiKey.lastUsedAt
                      ? new Date(apiKey.lastUsedAt).toLocaleDateString()
                      : 'Never'}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-2">
                      <button
                        onClick={() => setPendingDelete(apiKey)}
                        className="rounded-md p-1.5 text-surface-500 hover:bg-red-50 hover:text-red-600"
                        aria-label={`Revoke ${apiKey.name}`}
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
          title={`Revoke "${pendingDelete.name}"?`}
          description="Any app using this key will immediately lose access to flags. This can't be undone."
          confirmLabel="Revoke"
          isLoading={isDeleting}
          onConfirm={handleDeleteConfirmed}
          onCancel={() => setPendingDelete(null)}
        />
      )}
    </div>
  );
}
