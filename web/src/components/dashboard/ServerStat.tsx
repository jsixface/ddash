import React from 'react';
import type { LucideIcon } from 'lucide-react';
import { Tooltip } from '../ui/Tooltip';

interface ServerStatProps {
    icon: LucideIcon;
    label: string;
    value: string;
    unit: string;
    color: string;
    tooltip: string;
    iconColor: string;
    isDark: boolean;
}

export const ServerStat: React.FC<ServerStatProps> = ({ icon: Icon, label, value, unit, tooltip, iconColor, isDark }) => (
    <Tooltip content={tooltip} isDark={isDark}>
        <div className={`
      flex items-center gap-3 rounded-xl p-3 px-4 backdrop-blur-md shadow-sm transition-all cursor-default group border
      ${isDark
                ? 'bg-white/5 border-white/10 hover:bg-white/10'
                : 'bg-white/40 border-white/60 hover:shadow-md hover:bg-white/60'}
    `}>
            <div className={`p-2 rounded-lg transition-transform shadow-sm ${isDark ? 'bg-white/5' : 'bg-white/60'} ${iconColor} group-hover:scale-110`}>
                <Icon size={18} />
            </div>
            <div className="flex flex-col">
                <span className={`text-xs font-medium uppercase tracking-wider ${isDark ? 'text-slate-400' : 'text-slate-500'}`}>{label}</span>
                <span className={`text-sm font-semibold font-mono ${isDark ? 'text-slate-100' : 'text-slate-700'}`}>
                    {value}<span className={`text-xs ml-0.5 ${isDark ? 'text-slate-500' : 'text-slate-400'}`}>{unit}</span>
                </span>
            </div>
        </div>
    </Tooltip>
);
