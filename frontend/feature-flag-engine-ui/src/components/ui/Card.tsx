import { HTMLAttributes, ReactNode } from 'react';

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
}

export default function Card({ children, className = '', ...rest }: CardProps) {
  return (
    <div
      className={`rounded-xl border border-surface-200 bg-white shadow-sm ${className}`}
      {...rest}
    >
      {children}
    </div>
  );
}
