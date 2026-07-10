import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { getOrganizations } from '@/api/organizations';
import { ArrowLeft, Trash2, UserPlus } from 'lucide-react';
import {
  addSegmentMember,
  createSegment,
  getSegment,
  getSegmentMembers,
  removeSegmentMember,
  updateSegment,
} from '@/api/segments';
import { Organization } from '@/types/organization';
import { SegmentMember } from '@/types/segment';
import Card from '@/components/ui/Card';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import Button from '@/components/ui/Button';
import Spinner from '@/components/ui/Spinner';
import ErrorMessage from '@/components/ui/ErrorMessage';

export default function SegmentFormPage() {
  const { id } = useParams<{ id: string }>();
  const isEditMode = Boolean(id);
  const navigate = useNavigate();

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [organizationId, setOrganizationId] = useState('');
  const [organizations, setOrganizations] = useState<Organization[]>([]);

  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [members, setMembers] = useState<SegmentMember[]>([]);
  const [membersError, setMembersError] = useState<string | null>(null);
  const [newMemberIdentifier, setNewMemberIdentifier] = useState('');
  const [isAddingMember, setIsAddingMember] = useState(false);
  const [removingIdentifier, setRemovingIdentifier] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      setIsLoading(true);
      setLoadError(null);
      try {
        const orgs = await getOrganizations();
        setOrganizations(orgs);

        if (orgs.length === 0) {
          return;
        }

        if (id) {
          const [segment, segmentMembers] = await Promise.all([
            getSegment(id),
            getSegmentMembers(id),
          ]);
          setName(segment.name);
          setDescription(segment.description || '');
          setOrganizationId(segment.organizationId);
          setMembers(segmentMembers);
        } else {
          setOrganizationId(orgs[0].id);
        }
      } catch (err) {
        setLoadError(err instanceof Error ? err.message : 'Failed to load segment.');
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
      const payload = { name, description: description || undefined };
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

  async function handleAddMember(e: FormEvent) {
    e.preventDefault();
    if (!id || !newMemberIdentifier.trim()) return;
    setMembersError(null);
    setIsAddingMember(true);
    try {
      const member = await addSegmentMember(id, newMemberIdentifier.trim());
      setMembers((prev) => [...prev, member]);
      setNewMemberIdentifier('');
    } catch (err) {
      setMembersError(err instanceof Error ? err.message : 'Failed to add member.');
    } finally {
      setIsAddingMember(false);
    }
  }

  async function handleRemoveMember(userIdentifier: string) {
    if (!id) return;
    setMembersError(null);
    setRemovingIdentifier(userIdentifier);
    try {
      await removeSegmentMember(id, userIdentifier);
      setMembers((prev) => prev.filter((m) => m.userIdentifier !== userIdentifier));
    } catch (err) {
      setMembersError(err instanceof Error ? err.message : 'Failed to remove member.');
    } finally {
      setRemovingIdentifier(null);
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

      <Card className="max-w-lg p-6">
        {isLoading ? (
          <div className="flex justify-center py-8">
            <Spinner label="Loading segment…" />
          </div>
        ) : loadError ? (
          <ErrorMessage message={loadError} />
        ) : (
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            {organizations.length === 0 ? (
              <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                You need at least one organization before creating a segment.{' '}
                <Link to="/organizations/new" className="underline">
                  Create one
                </Link>
                .
              </div>
            ) : (
              <>
                {!isEditMode ? (
                  <Select
                    label="Organization"
                    name="organizationId"
                    required
                    value={organizationId}
                    onChange={(e) => setOrganizationId(e.target.value)}
                  >
                    {organizations.map((organization) => (
                      <option key={organization.id} value={organization.id}>
                        {organization.name}
                      </option>
                    ))}
                  </Select>
                ) : (
                  <Input
                    label="Organization"
                    name="organizationId"
                    disabled
                    value={
                      organizations.find((o) => o.id === organizationId)?.name ||
                      organizationId
                    }
                  />
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
                  label="Description (optional)"
                  name="description"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Who belongs in this segment"
                />
              </>
            )}

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

      {isEditMode && !isLoading && !loadError && (
        <Card className="max-w-lg p-6">
          <div className="mb-4">
            <h2 className="text-sm font-semibold text-surface-800">Members</h2>
            <p className="text-xs text-surface-500">
              User identifiers from your own system that belong to this segment.
            </p>
          </div>

          <form onSubmit={handleAddMember} className="mb-4 flex gap-2">
            <div className="flex-1">
              <Input
                aria-label="User identifier"
                placeholder="e.g. user_123"
                value={newMemberIdentifier}
                onChange={(e) => setNewMemberIdentifier(e.target.value)}
              />
            </div>
            <Button type="submit" isLoading={isAddingMember} disabled={!newMemberIdentifier.trim()}>
              <UserPlus className="h-4 w-4" />
              Add
            </Button>
          </form>

          {membersError && (
            <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
              {membersError}
            </div>
          )}

          {members.length === 0 ? (
            <p className="text-sm text-surface-500">No members yet.</p>
          ) : (
            <ul className="flex flex-col divide-y divide-surface-100">
              {members.map((member) => (
                <li
                  key={member.userIdentifier}
                  className="flex items-center justify-between py-2"
                >
                  <span className="text-sm text-surface-900">
                    {member.userIdentifier}
                  </span>
                  <button
                    type="button"
                    onClick={() => handleRemoveMember(member.userIdentifier)}
                    disabled={removingIdentifier === member.userIdentifier}
                    className="rounded-md p-1.5 text-surface-400 hover:bg-red-50 hover:text-red-600 disabled:cursor-not-allowed disabled:opacity-40"
                    aria-label={`Remove ${member.userIdentifier}`}
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}
    </div>
  );
}
