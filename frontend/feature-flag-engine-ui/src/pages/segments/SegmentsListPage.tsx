import { useEffect, useMemo, useState } from 'react';
import { getOrganizations } from "@/api/organizations";
import { Link } from 'react-router-dom';
import { Users, Pencil, Plus, Trash2 } from 'lucide-react';
import { Segment } from '@/types/segment';
import { Project } from '@/types/project';
import { deleteSegment, getSegments } from '@/api/segments';
import { getProjects } from '@/api/projects';
import Card from '@/components/ui/Card';
import Button from '@/components/ui/Button';
import Spinner from '@/components/ui/Spinner';
import ErrorMessage from '@/components/ui/ErrorMessage';
import EmptyState from '@/components/ui/EmptyState';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import Select from '@/components/ui/Select';
import Badge from '@/components/ui/Badge';

export default function SegmentsListPage() {
  const [segments, setSegments] = useState<Segment[]>([]);
  const [projects, setProjects] = useState<Project[]>([]);
  const [projectFilter, setProjectFilter] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<Segment | null>(null);
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

     let allSegments: Segment[] = [];

     for (const organization of organizations) {
       const segments = await getSegments(organization.id);
       allSegments.push(...segments);
     }

     setProjects(allProjects);
     setSegments(allSegments);

   } catch (err) {
     setError(
       err instanceof Error
         ? err.message
         : "Failed to load segments."
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

  const filteredSegments = useMemo(() => {
    if (!projectFilter) return segments;
    return segments.filter((s) => s.projectId === projectFilter);
  }, [segments, projectFilter]);

  async function handleDeleteConfirmed() {
    if (!pendingDelete) return;
    setIsDeleting(true);
    try {
      await deleteSegment(pendingDelete.id);
      setSegments((prev) => prev.filter((s) => s.id !== pendingDelete.id));
      setPendingDelete(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete segment.');
    } finally {
      setIsDeleting(false);
    }
  }

const getRuleCount = (segment: Segment) => segment.rules?.length ?? 0;


  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-semibold text-surface-900">Segments</h1>
          <p className="text-sm text-surface-500">
            Group users by attributes so you can target flags at specific
            audiences.
          </p>
        </div>
        <Link to="/segments/new">
          <Button>
            <Plus className="h-4 w-4" />
            New segment
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
          <Spinner label="Loading segments…" />
        </Card>
      )}

      {!isLoading && error && <ErrorMessage message={error} onRetry={loadData} />}

      {!isLoading && !error && filteredSegments.length === 0 && (
        <EmptyState
          icon={Users}
          title="No segments yet"
          description="Create a segment to target flags at a specific group of users."
          action={
            <Link to="/segments/new">
              <Button variant="secondary">
                <Plus className="h-4 w-4" />
                New segment
              </Button>
            </Link>
          }
        />
      )}

      {!isLoading && !error && filteredSegments.length > 0 && (
        <Card className="overflow-hidden">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-surface-200 bg-surface-50 text-surface-500">
              <tr>
                <th className="px-4 py-3 font-medium">Name</th>
                <th className="px-4 py-3 font-medium">Rules</th>
                <th className="hidden px-4 py-3 font-medium sm:table-cell">
                  Project
                </th>
                <th className="px-4 py-3 font-medium text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-surface-100">
              {filteredSegments.map((segment) => (
                <tr key={segment.id} className="hover:bg-surface-50">
                  <td className="px-4 py-3 font-medium text-surface-900">
                    {segment.name}
                  </td>
                  <td className="px-4 py-3">
                    <Badge tone="neutral">
                      {getRuleCount(segment)}{" "}
                      {getRuleCount(segment) === 1 ? "rule" : "rules"}
                    </Badge>
                  </td>
                  <td className="hidden px-4 py-3 text-surface-500 sm:table-cell">
                    {segment.projectName ||
                      projectNameById.get(segment.projectId) ||
                      '—'}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-2">
                      <Link
                        to={`/segments/${segment.id}/edit`}
                        className="rounded-md p-1.5 text-surface-500 hover:bg-surface-100 hover:text-surface-900"
                        aria-label={`Edit ${segment.name}`}
                      >
                        <Pencil className="h-4 w-4" />
                      </Link>
                      <button
                        onClick={() => setPendingDelete(segment)}
                        className="rounded-md p-1.5 text-surface-500 hover:bg-red-50 hover:text-red-600"
                        aria-label={`Delete ${segment.name}`}
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
          description="This can't be undone. Flags targeting this segment will stop matching it."
          isLoading={isDeleting}
          onConfirm={handleDeleteConfirmed}
          onCancel={() => setPendingDelete(null)}
        />
      )}
    </div>
  );
}
