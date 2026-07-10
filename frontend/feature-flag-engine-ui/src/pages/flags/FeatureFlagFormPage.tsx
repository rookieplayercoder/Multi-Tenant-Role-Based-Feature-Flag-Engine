import { FormEvent, useEffect, useState } from 'react';
import { Environment } from '@/types/environment';
import { getOrganizations } from '@/api/organizations';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import {
  createFeatureFlag,
  getFeatureFlag,
  getFlagEnvironmentStates,
  setFlagEnvironmentState,
  updateFeatureFlag,
} from '@/api/featureFlags';
import { getProjects } from '@/api/projects';
import { getEnvironments } from '@/api/environments';
import { Project } from '@/types/project';
import { FlagType } from '@/types/featureFlag';
import Card from '@/components/ui/Card';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import Button from '@/components/ui/Button';
import Spinner from '@/components/ui/Spinner';
import ErrorMessage from '@/components/ui/ErrorMessage';
import Toggle from '@/components/ui/Toggle';

const FLAG_TYPES: FlagType[] = ['boolean', 'string', 'number', 'json'];

interface EnvironmentToggleRow {
  environmentId: string;
  environmentName: string;
  enabled: boolean;
  isSaving: boolean;
}

export default function FeatureFlagFormPage() {
  const { id } = useParams<{ id: string }>();
  const isEditMode = Boolean(id);
  const navigate = useNavigate();

  const [key, setKey] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [type, setType] = useState<FlagType>('boolean');
  const [projectId, setProjectId] = useState('');
  const [environmentId, setEnvironmentId] = useState('');
  const [environments, setEnvironments] = useState<Environment[]>([]);

  const [projects, setProjects] = useState<Project[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [envRows, setEnvRows] = useState<EnvironmentToggleRow[]>([]);
  const [envError, setEnvError] = useState<string | null>(null);
  const [isEnvLoading, setIsEnvLoading] = useState(false);

  useEffect(() => {
    (async () => {
      setIsLoading(true);
      setLoadError(null);
      try {
       const organizations = await getOrganizations();

       if (organizations.length === 0) {
           setProjects([]);
           return;
       }



      const [projectsData, flag] = await Promise.all([
        getProjects(organizations[0].id),
        id ? getFeatureFlag(id) : Promise.resolve(null),
      ]);

      setProjects(projectsData);
        if (projectsData.length > 0) {
            const envs = await getEnvironments(projectsData[0].id);
            setEnvironments(envs);

            if (envs.length > 0) {
                setEnvironmentId(envs[0].id);
            }
        }

        if (flag) {
          setKey(flag.key);
          setName(flag.name);
          setDescription(flag.description || '');
          setType(flag.type);
          setProjectId(flag.projectId);
        } else if (projectsData.length > 0) {
          setProjectId(projectsData[0].id);
        }
      } catch (err) {
        setLoadError(
          err instanceof Error ? err.message : 'Failed to load feature flag.'
        );
      } finally {
        setIsLoading(false);
      }
    })();
  }, [id]);

useEffect(() => {
  if (!projectId) return;

  (async () => {
    const envs = await getEnvironments(projectId);
    setEnvironments(envs);

    if (envs.length > 0) {
      setEnvironmentId(envs[0].id);
    }
  })();
}, [projectId]);

  // Load per-environment toggle states once we're editing an existing flag.
  useEffect(() => {
    if (!id || !projectId) return;
    (async () => {
      setIsEnvLoading(true);
      setEnvError(null);
      try {
        const [environments, states] = await Promise.all([
          getEnvironments(projectId),
          getFlagEnvironmentStates(id),
        ]);
        const stateByEnvId = new Map(states.map((s) => [s.environmentId, s.enabled]));
        setEnvRows(
          environments.map((env) => ({
            environmentId: env.id,
            environmentName: env.name,
            enabled: stateByEnvId.get(env.id) ?? false,
            isSaving: false,
          }))
        );
      } catch (err) {
        setEnvError(
          err instanceof Error
            ? err.message
            : 'Failed to load environment toggles.'
        );
      } finally {
        setIsEnvLoading(false);
      }
    })();
  }, [id, projectId]);

  async function handleToggleEnvironment(environmentId: string, nextEnabled: boolean) {
    if (!id) return;
    setEnvRows((prev) =>
      prev.map((row) =>
        row.environmentId === environmentId ? { ...row, isSaving: true } : row
      )
    );
    try {
      await setFlagEnvironmentState(id, environmentId, nextEnabled);
      setEnvRows((prev) =>
        prev.map((row) =>
          row.environmentId === environmentId
            ? { ...row, enabled: nextEnabled, isSaving: false }
            : row
        )
      );
    } catch (err) {
      setEnvError(
        err instanceof Error ? err.message : 'Failed to update environment toggle.'
      );
      setEnvRows((prev) =>
        prev.map((row) =>
          row.environmentId === environmentId ? { ...row, isSaving: false } : row
        )
      );
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitError(null);
    setIsSubmitting(true);
    try {
      const payload = {
        projectId,
        key,
        name,
        description: description || undefined,
        type,
      };
      if (isEditMode && id) {
        await updateFeatureFlag(id, payload);
      } else {
        await createFeatureFlag(environmentId, payload);
      }
      navigate('/flags');
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : 'Failed to save feature flag.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <Link
          to="/flags"
          className="mb-2 inline-flex items-center gap-1 text-sm text-surface-500 hover:text-surface-900"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to feature flags
        </Link>
        <h1 className="text-xl font-semibold text-surface-900">
          {isEditMode ? 'Edit feature flag' : 'New feature flag'}
        </h1>
      </div>

      <Card className="max-w-lg p-6">
        {isLoading ? (
          <div className="flex justify-center py-8">
            <Spinner label="Loading feature flag…" />
          </div>
        ) : loadError ? (
          <ErrorMessage message={loadError} />
        ) : (
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            {projects.length === 0 ? (
              <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                You need at least one project before creating a feature flag.{' '}
                <Link to="/projects/new" className="underline">
                  Create one
                </Link>
                .
              </div>
            ) : (
              <>
                <Select
                  label="Project"
                  name="projectId"
                  required
                  value={projectId}
                  onChange={(e) => setProjectId(e.target.value)}
                >
                  {projects.map((project) => (
                    <option key={project.id} value={project.id}>
                      {project.name}
                    </option>
                  ))}
                </Select>

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
              </>
            )}

            <Input
              label="Key"
              name="key"
              required
              value={key}
              onChange={(e) => setKey(e.target.value)}
              placeholder="new-checkout-flow"
            />
            <Input
              label="Name"
              name="name"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="New checkout flow"
            />
            <Select
              label="Type"
              name="type"
              value={type}
              onChange={(e) => setType(e.target.value as FlagType)}
            >
              {FLAG_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </Select>
            <Input
              label="Description (optional)"
              name="description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="What this flag controls"
            />

            {submitError && (
              <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                {submitError}
              </div>
            )}

            <div className="mt-2 flex gap-3">
              <Button
                type="submit"
                isLoading={isSubmitting}
                disabled={projects.length === 0}
              >
                {isEditMode ? 'Save changes' : 'Create flag'}
              </Button>
              <Button
                type="button"
                variant="secondary"
                onClick={() => navigate('/flags')}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
            </div>
          </form>
        )}
      </Card>

      {isEditMode && !isLoading && !loadError && (
        <Card className="max-w-lg p-6">
          <h2 className="text-base font-semibold text-surface-900">
            Environment overrides
          </h2>
          <p className="mt-1 text-sm text-surface-500">
            Turn this flag on or off per environment.
          </p>

          <div className="mt-4">
            {isEnvLoading ? (
              <div className="flex justify-center py-6">
                <Spinner label="Loading environments…" />
              </div>
            ) : envError ? (
              <ErrorMessage message={envError} />
            ) : envRows.length === 0 ? (
              <p className="text-sm text-surface-500">
                No environments found for this project yet.
              </p>
            ) : (
              <ul className="divide-y divide-surface-100">
                {envRows.map((row) => (
                  <li
                    key={row.environmentId}
                    className="flex items-center justify-between py-3"
                  >
                    <span className="text-sm font-medium text-surface-800">
                      {row.environmentName}
                    </span>
                    <Toggle
                      checked={row.enabled}
                      disabled={row.isSaving}
                      label={`Toggle ${row.environmentName}`}
                      onChange={(next) =>
                        handleToggleEnvironment(row.environmentId, next)
                      }
                    />
                  </li>
                ))}
              </ul>
            )}
          </div>
        </Card>
      )}
    </div>
  );
}
