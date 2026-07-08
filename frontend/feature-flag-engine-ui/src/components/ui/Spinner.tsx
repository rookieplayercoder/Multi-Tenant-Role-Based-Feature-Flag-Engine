interface SpinnerProps {
  size?: 'sm' | 'md' | 'lg';
  label?: string;
}

const sizeClasses: Record<NonNullable<SpinnerProps['size']>, string> = {
  sm: 'h-4 w-4 border-2',
  md: 'h-8 w-8 border-2',
  lg: 'h-12 w-12 border-[3px]',
};

export default function Spinner({ size = 'md', label }: SpinnerProps) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 text-surface-500">
      <span
        className={`animate-spin rounded-full border-brand-500 border-t-transparent ${sizeClasses[size]}`}
      />
      {label && <span className="text-sm">{label}</span>}
    </div>
  );
}
