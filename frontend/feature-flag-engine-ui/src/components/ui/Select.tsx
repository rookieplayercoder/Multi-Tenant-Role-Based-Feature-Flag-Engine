import { SelectHTMLAttributes, forwardRef, ReactNode } from 'react';

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  children: ReactNode;
}

const Select = forwardRef<HTMLSelectElement, SelectProps>(
  ({ label, error, id, className = '', children, ...rest }, ref) => {
    const selectId = id || rest.name;
    return (
      <div className="flex flex-col gap-1.5">
        {label && (
          <label htmlFor={selectId} className="text-sm font-medium text-surface-800">
            {label}
          </label>
        )}
        <select
          ref={ref}
          id={selectId}
          className={`w-full rounded-lg border bg-white px-3 py-2 text-sm text-surface-900 focus:border-brand-500 focus:ring-1 focus:ring-brand-500 ${
            error ? 'border-red-400' : 'border-surface-200'
          } ${className}`}
          {...rest}
        >
          {children}
        </select>
        {error && <span className="text-xs text-red-600">{error}</span>}
      </div>
    );
  }
);

Select.displayName = 'Select';
export default Select;
