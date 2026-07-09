import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { getOrganizations } from '@/api/organizations';
import { ArrowLeft, Plus, Trash2 } from 'lucide-react';
import { createSegment, getSegment, updateSegment } from '@/api/segments';
import { getProjects } from '@/api/projects';
import { Project } from '@/types/project';
import { SegmentRule, SegmentRuleOperator } from '@/types/segment';
import Card from '@/components/ui/Card';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import Button from '@/components/ui/Button';
import Spinner from '@/components/ui/Spinner';
import ErrorMessage from '@/components/ui/ErrorMessage';

const OPERATORS: { value: SegmentRuleOperator; label: string }[] = [
  { value: 'equals', label: 'equals' },
  { value: 'not_equals', label: 'does not equal' },
  { value: 'contains', label: 'contains' },
  { value: 'in', label: 'is one of (comma-separated)' },
  { value: 'greater_than', label: 'greater than' },
  { value: 'less_than', label: 'less than' },
];

function emptyRule(): SegmentRule {
  return { attribute: '', operator: 'equals', value: '' };
}

export default function SegmentFormPage() {
  const { id } = useParams<{ id: string }>();
  const isEditMode = Boolean(id);
  const navigate = useNavigate();

  const [name, setName] = useState('');
  const [key, setKey] = useState('');
  const [description, setDescription] = useState('');
  const [projectId, setProjectId] = useState('');
  const [organizationId, setOrganizationId] = useState('');
  const [rules, setRules] = useState<SegmentRule[]>([emptyRule()]);

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
        setOrganizationId(organizations[0].id);

        if (organizations.length === 0) {
          setProjects([]);
          return;
        }

        const [projectsData, segment] = await Promise.all([
          getProjects(organizations[0].id),
          id ? getSegment(id) : Promise.resolve(null),
        ]);
        setProjects(projectsData);

        if (segment) {
          setName(segment.name);
          setKey(segment.key || '');
          setDescription(segment.description || '');
          setProjectId(segment.projectId);
          setRules(
            segment.rules && segment.rules.length > 0
              ? segment.rules
              : [emptyRule()]
          );
        } else if (projectsData.length > 0) {
          setProjectId(projectsData[0].id);
        }
      } catch (err) {
        setLoadError(err instanceof Error ? err.message : 'Failed to load segment.');
      } finally {
        setIsLoading(false);
      }
    })();
  }, [id]);

  function updateRule(index: number, patch: Partial<SegmentRule>) {
    setRules((prev) =>
      prev.map((rule, i) => (i === index ? { ...rule, ...patch } : rule))
    );
  }

  function addRule() {
    setRules((prev) => [...prev, emptyRule()]);
  }

  function removeRule(index: number) {
    setRules((prev) => prev.filter((_, i) => i !== index));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitError(null);

    const cleanedRules = rules.filter((r) => r.attribute.trim() !== '');
    if (cleanedRules.length === 0) {
      setSubmitError('Add at least one rule with an attribute.');
      return;
    }

    setIsSubmitting(true);
    try {
      const payload = {
        name,
        key: key || undefined,
        description: description || undefined,
        projectId,
        rules: cleanedRules,
      };
      if (isEditMode && id) {
        await updateSegment(id, payload);
      } else {
        await createSegment(organizationId, payload);
      }
      navigate('/segments');
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : 'Failed to save segment.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <Link
          to="/segments"
          className="mb-2 inline-flex items-center gap-1 text-sm text-surface-500 hover:text-surface-900"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to segments
        </Link>
        <h1 className="text-xl font-semibold text-surface-900">
          {isEditMode ? 'Edit segment' : 'New segment'}
        </h1>
      </div>

      <Card className="max-w-2xl p-6">
        {isLoading ? (
          <div className="flex justify-center py-8">
            <Spinner label="Loading segment…" />
          </div>
        ) : loadError ? (
          <ErrorMessage message={loadError} />
        ) : (
          <form onSubmit={handleSubmit} className="flex flex-col gap-6">
            <div className="flex flex-col gap-4">
              {projects.length === 0 ? (
                <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                  You need at least one project before creating a segment.{' '}
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
                placeholder="Beta testers"
              />
              <Input
                label="Key (optional)"
                name="key"
                value={key}
                onChange={(e) => setKey(e.target.value)}
                placeholder="beta-testers"
              />
              <Input
                label="Description (optional)"
                name="description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Who belongs in this segment"
              />
            </div>

            <div>
              <div className="mb-2 flex items-center justify-between">
                <h2 className="text-sm font-semibold text-surface-800">
                  Targeting rules
                </h2>
                <span className="text-xs text-surface-400">
                  Matches when all rules are true
                </span>
              </div>

              <div className="flex flex-col gap-3">
                {rules.map((rule, index) => (
                  <div
                    key={index}
                    className="flex flex-col gap-2 rounded-lg border border-surface-200 p-3 sm:flex-row sm:items-end"
                  >
                    <div className="flex-1">
                      <Input
                        label={index === 0 ? 'Attribute' : undefined}
                        placeholder="e.g. country, plan, userId"
                        value={rule.attribute}
                        onChange={(e) =>
                          updateRule(index, { attribute: e.target.value })
                        }
                      />
                    </div>
                    <div className="sm:w-56">
                      <Select
                        aria-label="Operator"
                        value={rule.operator}
                        onChange={(e) =>
                          updateRule(index, {
                            operator: e.target.value as SegmentRuleOperator,
                          })
                        }
                      >
                        {OPERATORS.map((op) => (
                          <option key={op.value} value={op.value}>
                            {op.label}
                          </option>
                        ))}
                      </Select>
                    </div>
                    <div className="flex-1">
                      <Input
                        placeholder="Value"
                        value={rule.value}
                        onChange={(e) => updateRule(index, { value: e.target.value })}
                      />
                    </div>
                    <button
                      type="button"
                      onClick={() => removeRule(index)}
                      disabled={rules.length === 1}
                      className="rounded-md p-2 text-surface-400 hover:bg-red-50 hover:text-red-600 disabled:cursor-not-allowed disabled:opacity-40"
                      aria-label="Remove rule"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                ))}
              </div>

              <Button
                type="button"
                variant="secondary"
                onClick={addRule}
                className="mt-3"
              >
                <Plus className="h-4 w-4" />
                Add rule
              </Button>
            </div>

            {submitError && (
              <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                {submitError}
              </div>
            )}

            <div className="flex gap-3">
              <Button
                type="submit"
                isLoading={isSubmitting}
                disabled={projects.length === 0}
              >
                {isEditMode ? 'Save changes' : 'Create segment'}
              </Button>
              <Button
                type="button"
                variant="secondary"
                onClick={() => navigate('/segments')}
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
