import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { createProject, getProject, updateProject } from '@/api/projects';
import { getOrganizations } from '@/api/organizations';
import { Organization } from '@/types/organization';
import Card from '@/components/ui/Card';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import Button from '@/components/ui/Button';
import Spinner from '@/components/ui/Spinner';
import ErrorMessage from '@/components/ui/ErrorMessage';

export default function ProjectFormPage() {
  const { id } = useParams<{ id: string }>();
  const isEditMode = Boolean(id);
  const navigate = useNavigate();

  const [name, setName] = useState('');
  const [key, setKey] = useState('');
  const [description, setDescription] = useState('');
  const [organizationId, setOrganizationId] = useState('');

  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    (async () => {
      setIsLoading(true);
      setLoadError(null);
      try {
        const [orgsData, project] = await Promise.all([
          getOrganizations(),
          id ? getProject(id) : Promise.resolve(null),
        ]);
        setOrganizations(orgsData);

        if (project) {
          setName(project.name);
          setKey(project.key || '');
          setDescription(project.description || '');
          setOrganizationId(project.organizationId);
        } else if (orgsData.length > 0) {
          setOrganizationId(orgsData[0].id);
        }
      } catch (err) {
        setLoadError(err instanceof Error ? err.message : 'Failed to load project.');
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
      const payload = {
        name,
        key: key || undefined,
        description: description || undefined,
        organizationId,
      };
      if (isEditMode && id) {
        await updateProject(id, payload);
      } else {
        await createProject(payload);
      }
      navigate('/projects');
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : 'Failed to save project.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <Link
          to="/projects"
          className="mb-2 inline-flex items-center gap-1 text-sm text-surface-500 hover:text-surface-900"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to projects
        </Link>
        <h1 className="text-xl font-semibold text-surface-900">
          {isEditMode ? 'Edit project' : 'New project'}
        </h1>
      </div>

      <Card className="max-w-lg p-6">
        {isLoading ? (
          <div className="flex justify-center py-8">
            <Spinner label="Loading project…" />
          </div>
        ) : loadError ? (
          <ErrorMessage message={loadError} />
        ) : (
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            {organizations.length === 0 ? (
              <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                You need at least one organization before creating a project.{' '}
                <Link to="/organizations/new" className="underline">
                  Create one
                </Link>
                .
              </div>
            ) : (
              <Select
                label="Organization"
                name="organizationId"
                required
                value={organizationId}
                onChange={(e) => setOrganizationId(e.target.value)}
              >
                {organizations.map((org) => (
                  <option key={org.id} value={org.id}>
                    {org.name}
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
              placeholder="Mobile App"
            />
            <Input
              label="Key (optional)"
              name="key"
              value={key}
              onChange={(e) => setKey(e.target.value)}
              placeholder="mobile-app"
            />
            <Input
              label="Description (optional)"
              name="description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="What this project is for"
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
                disabled={organizations.length === 0}
              >
                {isEditMode ? 'Save changes' : 'Create project'}
              </Button>
              <Button
                type="button"
                variant="secondary"
                onClick={() => navigate('/projects')}
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
