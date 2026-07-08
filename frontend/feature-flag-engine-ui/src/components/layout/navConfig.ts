import {
  LayoutDashboard,
  Building2,
  FolderKanban,
  Layers,
  Flag,
  Users,
  KeyRound,
  ScrollText,
  LucideIcon,
} from 'lucide-react';

export interface NavItem {
  label: string;
  to: string;
  icon: LucideIcon;
}

export const navItems: NavItem[] = [
  { label: 'Dashboard', to: '/dashboard', icon: LayoutDashboard },
  { label: 'Organizations', to: '/organizations', icon: Building2 },
  { label: 'Projects', to: '/projects', icon: FolderKanban },
  { label: 'Environments', to: '/environments', icon: Layers },
  { label: 'Feature Flags', to: '/flags', icon: Flag },
  { label: 'Segments', to: '/segments', icon: Users },
  { label: 'API Keys', to: '/api-keys', icon: KeyRound },
  { label: 'Audit Logs', to: '/audit-logs', icon: ScrollText },
];
