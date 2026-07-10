import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getOrganizations } from '@/api/organizations';
import { getProjects } from '@/api/projects';
import { ArrowLeft, Check, Copy, ShieldAlert } from 'lucide-react';
import { createApiKey } from '@/api/apiKeys';
import { getEnvironments } from '@/api/environments';
import { Environment } from '@/types/environment';
import { ApiKey, ApiKeyType } from '@/types/apiKey';
import Card from '@/components/ui/Card';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import Button from '@/components/ui/Button';
import Spinner from '@/components/ui/Spinner';
import ErrorMessage from '@/components/ui/ErrorMessage';

export default function ApiKeyCreatePage() {
  const navigate = useNavigate();

  const [name, setName] = useState('');
  const [type, setType] = useState<ApiKeyType>('server');
  const [environmentId, setEnvironmentId] = useState('');

  const [environments, setEnvironments] = useState<Environment[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [createdKey, setCreatedKey] = useState<ApiKey | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    (async () => {
      setIsLoading(true);
      setLoadError(null);

      try {
        const organizations = await getOrganizations();

        let allProjects = [];

        for (const organization of organizations) {
          const projects = await getProjects(organization.id);
          allProjects.push(...projects);
        }

        let allEnvironments = [];

        for (const project of allProjects) {
          const environments = await getEnvironments(project.id);
          allEnvironments.push(...environments);
        }

        setEnvironments(allEnvironments);

        if (allEnvironments.length > 0) {
          setEnvironmentId(allEnvironments[0].id);
        }

      } catch (err) {
        setLoadError(
          err instanceof Error
            ? err.message
            : "Failed to load environments."
        );
      } finally {
        setIsLoading(false);
      }
    })();
  }, []);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitError(null);
    setIsSubmitting(true);
    try {
      const key = await createApiKey(
        environmentId,
        {
          name,
          type,
          environmentId,
        }
      );
      setCreatedKey(key);
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : 'Failed to create API key.');
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleCopy() {
    if (!createdKey?.key) return;
    await navigator.clipboard.writeText(createdKey.key);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <Link
          to="/api-keys"
          className="mb-2 inline-flex items-center gap-1 text-sm text-surface-500 hover:text-surface-900"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to API keys
        </Link>
        <h1 className="text-xl font-semibold text-surface-900">New API key</h1>
      </div>

      <Card className="max-w-lg p-6">
        {createdKey ? (
          <div className="flex flex-col gap-4">
            <div className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 p-3">
              <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" />
              <p className="text-sm text-amber-800">
                Copy this key now — for security, you won't be able to see it
                again after leaving this page.
              </p>
            </div>

            <div>
              <p className="mb-1.5 text-sm font-medium text-surface-800">
                {createdKey.name}
              </p>
              <div className="flex items-center gap-2 rounded-lg border border-surface-200 bg-surface-50 px-3 py-2">
                <code className="flex-1 overflow-x-auto whitespace-nowrap font-mono text-sm text-surface-900">
                  {createdKey.key}
                </code>
                <button
                  onClick={handleCopy}
                  className="rounded-md p-1.5 text-surface-500 hover:bg-surface-200 hover:text-surface-900"
                  aria-label="Copy API key"
                >
                  {copied ? (
                    <Check className="h-4 w-4 text-green-600" />
                  ) : (
                    <Copy className="h-4 w-4" />
                  )}
                </button>
              </div>
            </div>

            <Button onClick={() => navigate('/api-keys')} className="mt-2 w-fit">
              Done
            </Button>
          </div>
        ) : isLoading ? (
          <div className="flex justify-center py-8">
            <Spinner label="Loading environments…" />
          </div>
        ) : loadError ? (
          <ErrorMessage message={loadError} />
        ) : (
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            {environments.length === 0 ? (
              <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                You need at least one environment before creating an API key.{' '}
                <Link to="/environments/new" className="underline">
                  Create one
                </Link>
                .
              </div>
            ) : (
              <Select
                label="Environment"
                name="environmentId"
                required
                value={environmentId}
                onChange={(e) => setEnvironmentId(e.target.value)}
              >
                {environments.map((env) => (
                  <option key={env.id} value={env.id}>
                    {env.name}
                  </option>
                ))}
              </Select>
            )}

            <Input
              label="Name"
              name="name"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Backend service"
            />

            <Select
              label="Type"
              name="type"
              value={type}
              onChange={(e) => setType(e.target.value as ApiKeyType)}
            >
              <option value="server">Server (full access, keep secret)</option>
              <option value="client">Client (safe for browsers/mobile apps)</option>
            </Select>

            {submitError && (
              <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                {submitError}
              </div>
            )}

            <div className="mt-2 flex gap-3">
              <Button
                type="submit"
                isLoading={isSubmitting}
                disabled={environments.length === 0}
              >
                Create key
              </Button>
              <Button
                type="button"
                variant="secondary"
                onClick={() => navigate('/api-keys')}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
            </div>
          </form>
        )}
      </Card>
    </div>
  );
}
