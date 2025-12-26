import React from 'react';
import type {AppStatus} from '../../types/dashboard';

export const StatusDot: React.FC<{ status: AppStatus }> = ({ status }) => {
    const colors: Record<AppStatus, string> = {
        RUNNING: "bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.4)]",
        PAUSED: "bg-sky-400 shadow-[0_0_8px_rgba(56,189,248,0.4)]",
        RESTARTING: "bg-amber-400 shadow-[0_0_8px_rgba(251,191,36,0.4)]",
        EXITED: "bg-slate-300",
        CREATED: "bg-slate-300",
        REMOVING: "bg-slate-300",
        DEAD: "bg-slate-300",
    };

    return (
        <div className={`h-2.5 w-2.5 rounded-full ${colors[status] || colors.EXITED}`} />
    );
};
