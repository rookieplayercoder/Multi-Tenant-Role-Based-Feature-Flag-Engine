import { NavLink } from 'react-router-dom';
import { X, FlagTriangleRight } from 'lucide-react';
import { navItems } from './navConfig';

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function Sidebar({ isOpen, onClose }: SidebarProps) {
  return (
    <>
      {/* Mobile backdrop */}
      {isOpen && (
        <div
          className="fixed inset-0 z-30 bg-black/30 lg:hidden"
          onClick={onClose}
          aria-hidden="true"
        />
      )}

      <aside
        className={`fixed inset-y-0 left-0 z-40 flex w-64 flex-col border-r border-surface-200 bg-white transition-transform duration-200 ease-in-out dark:border-surface-700 dark:bg-surface-800 lg:static lg:translate-x-0 ${
          isOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="flex h-16 items-center justify-between border-b border-surface-200 px-5 dark:border-surface-700">
          <div className="flex items-center gap-2">
            <FlagTriangleRight className="h-6 w-6 text-brand-600 dark:text-brand-400" />
            <span className="font-display text-base font-semibold text-surface-900 dark:text-surface-50">
              Flag Engine
            </span>
          </div>
          <button
            onClick={onClose}
            className="rounded-md p-1 text-surface-500 hover:bg-surface-100 dark:text-surface-400 dark:hover:bg-surface-700 lg:hidden"
            aria-label="Close sidebar"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-4">
          {navItems.map(({ label, to, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              onClick={onClose}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-brand-50 text-brand-700 dark:bg-brand-900/40 dark:text-brand-300'
                    : 'text-surface-600 hover:bg-surface-100 hover:text-surface-900 dark:text-surface-300 dark:hover:bg-surface-700 dark:hover:text-surface-50'
                }`
              }
            >
              <Icon className="h-4 w-4" />
              {label}
            </NavLink>
          ))}
        </nav>

        <div className="border-t border-surface-200 p-4 text-xs text-surface-400 dark:border-surface-700 dark:text-surface-600">
          Feature Flag Engine
        </div>
      </aside>
    </>
  );
}
