import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import {
  createEnvironment,
  getEnvironment,
  updateEnvironment,
} from '@/api/environments';
import { getProjects } from '@/api/projects';
import { getOrganizations } from '@/api/organizations';
import { Project } from '@/types/project';
import { EnvironmentKey } from '@/types/environment';
import Card from '@/components/ui/Card';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import Button from '@/components/ui/Button';
import Spinner from '@/components/ui/Spinner';
import ErrorMessage from '@/components/ui/ErrorMessage';

const ENVIRONMENT_KEYS: { value: EnvironmentKey; label: string }[] = [
  { value: 'DEV', label: 'DEV' },
  { value: 'TEST', label: 'TEST' },
  { value: 'STAGING', label: 'STAGING' },
  { value: 'PROD', label: 'PROD' },
];

export default function EnvironmentFormPage() {
  const { id } = useParams<{ id: string }>();
  const isEditMode = Boolean(id);
  const navigate = useNavigate();

  const [name, setName] = useState('');
  const [key, setKey] = useState<EnvironmentKey>('DEV');
  const [description, setDescription] = useState('');
  const [projectId, setProjectId] = useState('');

  const [projects, setProjects] = useState<Project[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

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

        const projectsData = await getProjects(organizations[0].id);
        setProjects(projectsData);

        const environment = id ? await getEnvironment(id) : null;

        if (environment) {
          setName(environment.name);
          setKey(environment.key);
          setDescription(environment.description || '');
          setProjectId(environment.projectId);
        } else if (projectsData.length > 0) {
          setProjectId(projectsData[0].id);
        }
      } catch (err) {
        setLoadError(
          err instanceof Error ? err.message : 'Failed to load environment.'
        );
      } finally {
        setIsLoading(false);
      }
    })();
  }, [id]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitError(null);
    setIsSubmitting(true);
    try {
      if (isEditMode && id) {
        // Key is immutable once set — the update endpoint only accepts name.
        await updateEnvironment(id, {
          name,
          key,
          description: description || undefined,
          projectId,
        });
      } else {
        await createEnvironment(projectId, {
          name,
          key,
          description: description || undefined,
          projectId,
        });
      }
      navigate('/environments');
    } catch (err) {
      setSubmitError(
        err instanceof Error ? err.message : 'Failed to save environment.'
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <Link
          to="/environments"
          className="mb-2 inline-flex items-center gap-1 text-sm text-surface-500 hover:text-surface-900"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to environments
        </Link>
        <h1 className="text-xl font-semibold text-surface-900">
          {isEditMode ? 'Edit environment' : 'New environment'}
        </h1>
      </div>

      <Card className="max-w-lg p-6">
        {isLoading ? (
          <div className="flex justify-center py-8">
            <Spinner label="Loading environment…" />
          </div>
        ) : loadError ? (
          <ErrorMessage message={loadError} />
        ) : (
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            {projects.length === 0 ? (
              <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                You need at least one project before creating an environment.{' '}
                <Link to="/projects/new" className="underline">
                  Create one
                </Link>
                .
              </div>
            ) : (
              <Select
                label="Project"
                name="projectId"
                required
                disabled={isEditMode}
                value={projectId}
                onChange={(e) => setProjectId(e.target.value)}
              >
                {projects.map((project) => (
                  <option key={project.id} value={project.id}>
                    {project.name}
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
              placeholder="Production"
            />

            <Select
              label={isEditMode ? 'Key (cannot be changed)' : 'Key'}
              name="key"
              required
              disabled={isEditMode}
              value={key}
              onChange={(e) => setKey(e.target.value as EnvironmentKey)}
            >
              {ENVIRONMENT_KEYS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>

            <Input
              label="Description (optional)"
              name="description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="What this environment is used for"
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
                {isEditMode ? 'Save changes' : 'Create environment'}
              </Button>
              <Button
                type="button"
                variant="secondary"
                onClick={() => navigate('/environments')}
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
