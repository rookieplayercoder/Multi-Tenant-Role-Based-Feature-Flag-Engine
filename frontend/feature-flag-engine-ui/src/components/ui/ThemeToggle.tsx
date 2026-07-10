import { Moon, Sun } from 'lucide-react';
import { useTheme } from '@/context/ThemeContext';

export default function ThemeToggle() {
  const { theme, toggleTheme } = useTheme();
  const isDark = theme === 'dark';

  return (
    <button
      type="button"
      role="switch"
      aria-checked={isDark}
      aria-label={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
      onClick={toggleTheme}
      className={`relative inline-flex h-8 w-14 shrink-0 items-center rounded-full border transition-colors duration-200 ${
        isDark
          ? 'border-surface-700 bg-surface-700'
          : 'border-surface-200 bg-surface-100'
      }`}
    >
      <span
        className={`absolute left-0.5 flex h-6 w-6 items-center justify-center rounded-full bg-white shadow-sm transition-transform duration-200 ${
          isDark ? 'translate-x-6' : 'translate-x-0'
        }`}
      >
        {isDark ? (
          <Moon className="h-3.5 w-3.5 text-surface-700" />
        ) : (
          <Sun className="h-3.5 w-3.5 text-ignition-500" />
        )}
      </span>
    </button>
  );
}
