import React from 'react';
import type {AppStatus, AppHealth} from '../../types/dashboard';

interface StatusDotProps {
    status: AppStatus;
    health?: AppHealth;
}

export const StatusDot: React.FC<StatusDotProps> = ({ status, health }) => {
    const colors: Record<AppStatus, string> = {
        RUNNING: "bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.4)]",
        PAUSED: "bg-sky-400 shadow-[0_0_8px_rgba(56,189,248,0.4)]",
        RESTARTING: "bg-amber-400 shadow-[0_0_8px_rgba(251,191,36,0.4)]",
        EXITED: "bg-slate-300",
        CREATED: "bg-slate-300",
        REMOVING: "bg-slate-300",
        DEAD: "bg-slate-300",
        EXTERNAL: "bg-indigo-400 shadow-[0_0_8px_rgba(129,140,248,0.4)]",
    };

    if (status === 'RUNNING' || status === 'EXTERNAL') {
        if (health === 'UNHEALTHY') {
            return (
                <div className="relative flex h-2.5 w-2.5">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-rose-400 opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-rose-500 shadow-[0_0_8px_rgba(244,63,94,0.4)]"></span>
                </div>
            );
        }
        if (health === 'STARTING') {
            return (
                <div className="relative flex h-2.5 w-2.5">
                    <span className="animate-pulse absolute inline-flex h-full w-full rounded-full bg-amber-400 opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-amber-500 shadow-[0_0_8px_rgba(245,158,11,0.4)]"></span>
                </div>
            );
        }
    }

    return (
        <div className={`h-2.5 w-2.5 rounded-full ${colors[status] || colors.EXITED}`} />
    );
};
