import { FormEvent, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { Link } from 'react-router-dom';
import {
  createOrganization,
  getOrganization,
  updateOrganization,
} from '@/api/organizations';
import Card from '@/components/ui/Card';
import Input from '@/components/ui/Input';
import Button from '@/components/ui/Button';
import Spinner from '@/components/ui/Spinner';
import ErrorMessage from '@/components/ui/ErrorMessage';

export default function OrganizationFormPage() {
  const { id } = useParams<{ id: string }>();
  const isEditMode = Boolean(id);
  const navigate = useNavigate();

  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [description, setDescription] = useState('');

  const [isLoading, setIsLoading] = useState(isEditMode);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!id) return;
    (async () => {
      setIsLoading(true);
      setLoadError(null);
      try {
        const org = await getOrganization(id);
        setName(org.name);
        setSlug(org.slug || '');
        setDescription(org.description || '');
      } catch (err) {
        setLoadError(
          err instanceof Error ? err.message : 'Failed to load organization.'
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
      const payload = { name, slug: slug || undefined, description: description || undefined };
      if (isEditMode && id) {
        await updateOrganization(id, payload);
      } else {
        await createOrganization(payload);
      }
      navigate('/organizations');
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : 'Failed to save organization.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <Link
          to="/organizations"
          className="mb-2 inline-flex items-center gap-1 text-sm text-surface-500 hover:text-surface-900"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to organizations
        </Link>
        <h1 className="text-xl font-semibold text-surface-900">
          {isEditMode ? 'Edit organization' : 'New organization'}
        </h1>
      </div>

      <Card className="max-w-lg p-6">
        {isLoading ? (
          <div className="flex justify-center py-8">
            <Spinner label="Loading organization…" />
          </div>
        ) : loadError ? (
          <ErrorMessage message={loadError} />
        ) : (
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <Input
              label="Name"
              name="name"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Acme Corp"
            />
            <Input
              label="Slug (optional)"
              name="slug"
              value={slug}
              onChange={(e) => setSlug(e.target.value)}
              placeholder="acme-corp"
            />
            <Input
              label="Description (optional)"
              name="description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="What this organization is for"
            />

            {submitError && (
              <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                {submitError}
              </div>
            )}

            <div className="mt-2 flex gap-3">
              <Button type="submit" isLoading={isSubmitting}>
                {isEditMode ? 'Save changes' : 'Create organization'}
              </Button>
              <Button
                type="button"
                variant="secondary"
                onClick={() => navigate('/organizations')}
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
