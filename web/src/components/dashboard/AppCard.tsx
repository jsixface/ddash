import React from 'react';
import { ExternalLink, FileText, Pin, Power, RefreshCw, X } from 'lucide-react';
import type { AppData, MenuItem } from '../../types/dashboard';
import { ContextMenu } from '../ui/ContextMenu';
import { StatusDot } from '../ui/StatusDot';

interface AppCardProps {
    app: AppData;
    editMode: boolean;
    isDark: boolean;
}

export const AppCard: React.FC<AppCardProps> = ({ app, editMode, isDark }) => {
    const Icon = app.icon;

    const handleLaunch = () => {
        if (app.url) {
            window.open(app.url, '_blank', 'noopener,noreferrer');
        }
    };

    const menuItems: MenuItem[] = [
        { label: "Launch App", icon: ExternalLink, action: handleLaunch },
        { label: "Pin to Home", icon: Pin, action: () => console.log("Pin") },
        { label: "View Logs", icon: FileText, action: () => console.log("Logs") },
        { label: "Restart Container", icon: RefreshCw, action: () => console.log("Restart") },
        { label: "Stop Service", icon: Power, action: () => console.log("Stop"), variant: 'danger' },
    ];

    return (
        <ContextMenu menuItems={menuItems} isDark={isDark}>
            <div
                onClick={() => !editMode && handleLaunch()}
                className={`
        relative group flex flex-col items-center justify-center 
        aspect-square p-3 md:p-4 rounded-xl md:rounded-2xl backdrop-blur-sm border
        transition-all duration-300 ease-out
        ${editMode ? 'animate-pulse cursor-move' : 'hover:-translate-y-1 cursor-pointer'}
        ${isDark
                        ? 'bg-gradient-to-br from-white/5 to-white/0 border-white/5 hover:border-white/20 hover:bg-white/10'
                        : 'bg-white/60 border-slate-200/60 shadow-sm hover:shadow-md hover:border-violet-200 hover:bg-white/80'}
      `}>
                {editMode && (
                    <div className="absolute top-1.5 right-1.5 md:top-2 md:right-2 p-1 bg-rose-100 text-rose-500 rounded-full hover:bg-rose-500 hover:text-white transition-colors z-20">
                        <X size={10} className="md:w-3 md:h-3" />
                    </div>
                )}

                {/* Hover Glow Effect */}
                <div className={`
          absolute inset-0 rounded-xl md:rounded-2xl transition-all duration-500
          bg-gradient-to-br 
          ${isDark
                        ? 'from-indigo-500/0 via-indigo-500/0 to-indigo-500/0 group-hover:to-indigo-500/10'
                        : 'from-violet-200/0 via-violet-200/0 to-violet-200/0 group-hover:to-violet-200/20'}
        `} />

                <div className={`
          relative mb-2 md:mb-3 p-2 md:p-3 rounded-lg md:rounded-xl transition-transform duration-300 shadow-sm
          ${isDark
                        ? 'bg-slate-900/50 shadow-lg group-hover:shadow-indigo-500/20'
                        : 'bg-white shadow-slate-200/50 group-hover:scale-110 border border-slate-100'}
        `}>
                    <Icon size={24} className={`md:w-8 md:h-8 transition-colors ${isDark ? 'text-slate-200 group-hover:text-indigo-300' : 'text-slate-500 group-hover:text-violet-500'}`} />
                </div>

                <h3 className={`text-xs md:text-sm font-semibold text-center line-clamp-1 transition-colors ${isDark ? 'text-slate-200 group-hover:text-white' : 'text-slate-700 group-hover:text-violet-700'}`}>
                    {app.name}
                </h3>

                <div className="mt-1.5 md:mt-2 flex items-center gap-1.5 md:gap-2">
                    <StatusDot status={app.status} />
                    <span className={`text-[9px] md:text-[10px] font-mono font-medium ${isDark ? (app.status === 'EXITED' ? 'text-slate-500' : 'text-slate-400') : (app.status === 'EXITED' ? 'text-slate-400' : 'text-slate-500')}`}>
                        {app.status}
                    </span>
                </div>
            </div>
        </ContextMenu>
    );
};
