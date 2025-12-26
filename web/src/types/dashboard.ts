import type {LucideIcon} from 'lucide-react';

export type AppStatus = 'RUNNING' | 'EXITED' | 'RESTARTING' | 'CREATED' | 'PAUSED' | 'REMOVING' | 'DEAD';

export interface AppData {
    id: number;
    name: string;
    url: string;
    category: string;
    icon: LucideIcon;
    status: AppStatus;
    ping: string;
}

export interface MenuItem {
    label: string;
    icon: LucideIcon;
    action: () => void;
    variant?: 'default' | 'danger';
}

export interface ServerStats {
    cpu: string;
    ram: string;
    temp: string;
}
