import React from 'react';
import type { HealthStatus } from '../../types/dashboard';
import { CheckCircle2, AlertCircle, Loader2 } from 'lucide-react';

interface HealthBadgeProps {
    status: HealthStatus;
    isDark: boolean;
}

export const HealthBadge: React.FC<HealthBadgeProps> = ({ status, isDark }) => {
    if (status === 'NONE') return null;

    const config = {
        HEALTHY: {
            icon: CheckCircle2,
            text: 'Healthy',
            className: isDark ? 'text-emerald-400 bg-emerald-400/10' : 'text-emerald-600 bg-emerald-50',
        },
        UNHEALTHY: {
            icon: AlertCircle,
            text: 'Unhealthy',
            className: isDark ? 'text-rose-400 bg-rose-400/10' : 'text-rose-600 bg-rose-50',
        },
        STARTING: {
            icon: Loader2,
            text: 'Starting',
            className: isDark ? 'text-amber-400 bg-amber-400/10' : 'text-amber-600 bg-amber-50',
        }
    };

    const { icon: Icon, text, className } = config[status as keyof typeof config];

    return (
        <div className={`flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-medium ${className}`}>
            <Icon size={10} className={status === 'STARTING' ? 'animate-spin' : ''} />
            <span>{text}</span>
        </div>
    );
};
