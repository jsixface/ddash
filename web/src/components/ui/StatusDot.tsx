import React from 'react';
import type { AppStatus } from '../../types/dashboard';

export const StatusDot: React.FC<{ status: AppStatus }> = ({ status }) => {
    const colors: Record<AppStatus, string> = {
        running: "bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.4)]",
        idle: "bg-sky-400 shadow-[0_0_8px_rgba(56,189,248,0.4)]",
        warning: "bg-amber-400 shadow-[0_0_8px_rgba(251,191,36,0.4)]",
        stopped: "bg-slate-300",
    };

    return (
        <div className={`h-2.5 w-2.5 rounded-full ${colors[status] || colors.stopped}`} />
    );
};
