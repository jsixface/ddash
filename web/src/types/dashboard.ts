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
    health: HealthStatus;
}

export interface MenuItem {
    label: string;
    icon: LucideIcon;
    action: () => void;
    variant?: 'default' | 'danger';
}
