import { LucideIcon } from 'lucide-react';
import { ReactNode } from 'react';

interface EmptyStateProps {
  icon: LucideIcon;
  title: string;
  description?: string;
  action?: ReactNode;
}

export default function EmptyState({
  icon: Icon,
  title,
  description,
  action,
}: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-surface-200 bg-white px-6 py-14 text-center">
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-surface-100">
        <Icon className="h-6 w-6 text-surface-400" />
      </div>
      <div>
        <p className="text-sm font-medium text-surface-900">{title}</p>
        {description && (
          <p className="mt-1 text-sm text-surface-500">{description}</p>
        )}
      </div>
      {action}
    </div>
  );
}
