import type { LucideIcon } from 'lucide-react';

export type AppStatus = 'RUNNING' | 'EXITED' | 'RESTARTING' | 'CREATED' | 'PAUSED' | 'REMOVING' | 'DEAD' | 'EXTERNAL';
export type HealthStatus = 'HEALTHY' | 'UNHEALTHY' | 'STARTING' | 'NONE';

export interface AppData {
    id: string;
    name: string;
    url: string;
    category: string;
    icon: LucideIcon;
    status: AppStatus;
    ping: string;
    description?: string;
    order: number;
    health: HealthStatus;
}

export interface MenuItem {
    label: string;
    icon: LucideIcon;
    action: () => void;
    variant?: 'default' | 'danger';
}

export interface SessionInfo {
    /** OIDC login is configured on the server. When false every action is open to everyone. */
    authEnabled: boolean;
    /** The current visitor may start/stop/restart containers and read logs. */
    canManage: boolean;
    user?: { name: string; email?: string } | null;
}
