import type { LucideIcon } from 'lucide-react';

export type AppStatus = 'RUNNING' | 'EXITED' | 'RESTARTING' | 'CREATED' | 'PAUSED' | 'REMOVING' | 'DEAD' | 'EXTERNAL';
export type AppHealth = 'HEALTHY' | 'UNHEALTHY' | 'STARTING' | 'NONE';

export interface AppData {
    id: string;
    name: string;
    url: string;
    category: string;
    icon: LucideIcon;
    status: AppStatus;
    health: AppHealth;
    ping: string;
    description?: string;
}

export interface MenuItem {
    label: string;
    icon: LucideIcon;
    action: () => void;
    variant?: 'default' | 'danger';
}


