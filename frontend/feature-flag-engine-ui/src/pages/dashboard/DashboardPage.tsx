import { Building2, FolderKanban, Flag, Layers, ArrowUpRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import Card from '@/components/ui/Card';
import { useAuth } from '@/hooks/useAuth';

const summaryCards = [
  {
    label: 'Organizations',
    icon: Building2,
    to: '/organizations',
    iconBg: 'bg-brand-50 dark:bg-brand-900/30',
    iconColor: 'text-brand-600 dark:text-brand-400',
  },
  {
    label: 'Projects',
    icon: FolderKanban,
    to: '/projects',
    iconBg: 'bg-violet-50 dark:bg-violet-900/30',
    iconColor: 'text-violet-600 dark:text-violet-400',
  },
  {
    label: 'Environments',
    icon: Layers,
    to: '/environments',
    iconBg: 'bg-sky-50 dark:bg-sky-900/30',
    iconColor: 'text-sky-600 dark:text-sky-400',
  },
  {
    label: 'Feature Flags',
    icon: Flag,
    to: '/flags',
    iconBg: 'bg-ignition-50 dark:bg-ignition-900/30',
    iconColor: 'text-ignition-600 dark:text-ignition-400',
  },
];

export default function DashboardPage() {
  const { user } = useAuth();

  return (
    <div className="flex flex-col gap-6">
      <div className="overflow-hidden rounded-xl bg-gradient-to-br from-brand-600 via-brand-600 to-brand-800 px-6 py-8 text-white sm:px-8">
        <p className="font-mono text-xs uppercase tracking-widest text-white/60">
          Dashboard
        </p>
        <h1 className="mt-1 font-display text-2xl font-semibold tracking-tight sm:text-3xl">
          Welcome{user?.fullName ? `, ${user.fullName}` : ''}
        </h1>
        <p className="mt-2 max-w-xl text-sm text-white/70">
          Here's a quick overview of your feature flag setup across
          organizations, projects, and environments.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {summaryCards.map(({ label, icon: Icon, to, iconBg, iconColor }) => (
          <Link key={label} to={to}>
            <Card className="group flex items-center gap-4 p-5 transition-shadow hover:shadow-md">
              <div
                className={`flex h-11 w-11 items-center justify-center rounded-lg ${iconBg}`}
              >
                <Icon className={`h-5 w-5 ${iconColor}`} />
              </div>
              <div className="flex-1">
                <p className="text-sm text-surface-500 dark:text-surface-400">{label}</p>
                <p className="text-lg font-semibold text-surface-900 dark:text-surface-50">—</p>
              </div>
              <ArrowUpRight className="h-4 w-4 text-surface-300 opacity-0 transition-opacity group-hover:opacity-100 dark:text-surface-600" />
            </Card>
          </Link>
        ))}
      </div>

      <Card className="p-6">
        <p className="text-sm text-surface-500 dark:text-surface-400">
          This dashboard will be wired up to live counts and recent activity in a
          later step, once we build out each feature's API integration.
        </p>
      </Card>
    </div>
  );
}
