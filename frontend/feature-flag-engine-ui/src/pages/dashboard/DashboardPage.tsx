import { Building2, FolderKanban, Flag, Layers } from 'lucide-react';
import Card from '@/components/ui/Card';
import { useAuth } from '@/hooks/useAuth';

const summaryCards = [
  { label: 'Organizations', icon: Building2, to: '/organizations' },
  { label: 'Projects', icon: FolderKanban, to: '/projects' },
  { label: 'Environments', icon: Layers, to: '/environments' },
  { label: 'Feature Flags', icon: Flag, to: '/flags' },
];

export default function DashboardPage() {
  const { user } = useAuth();

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold text-surface-900">
          Welcome{user?.name ? `, ${user.name}` : ''}
        </h1>
        <p className="text-sm text-surface-500">
          Here's a quick overview of your feature flag setup.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {summaryCards.map(({ label, icon: Icon }) => (
          <Card key={label} className="flex items-center gap-4 p-5">
            <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-brand-50">
              <Icon className="h-5 w-5 text-brand-600" />
            </div>
            <div>
              <p className="text-sm text-surface-500">{label}</p>
              <p className="text-lg font-semibold text-surface-900">—</p>
            </div>
          </Card>
        ))}
      </div>

      <Card className="p-6">
        <p className="text-sm text-surface-500">
          This dashboard will be wired up to live counts and recent activity in a
          later step, once we build out each feature's API integration.
        </p>
      </Card>
    </div>
  );
}
