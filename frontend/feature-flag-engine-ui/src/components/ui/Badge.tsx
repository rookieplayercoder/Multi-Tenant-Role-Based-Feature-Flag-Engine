import { ReactNode } from 'react';

interface BadgeProps {
  children: ReactNode;
  tone?: 'neutral' | 'brand' | 'green' | 'red';
}

const toneClasses: Record<NonNullable<BadgeProps['tone']>, string> = {
  neutral: 'bg-surface-100 text-surface-600',
  brand: 'bg-brand-50 text-brand-700',
  green: 'bg-green-50 text-green-700',
  red: 'bg-red-50 text-red-700',
};

export default function Badge({ children, tone = 'neutral' }: BadgeProps) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${toneClasses[tone]}`}
    >
      {children}
    </span>
  );
}
