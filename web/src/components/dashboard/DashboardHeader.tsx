import React from 'react';

interface DashboardHeaderProps {
    time: Date;
    isDark: boolean;
}

export const DashboardHeader: React.FC<DashboardHeaderProps> = ({ time, isDark }) => {
    return (
        <header className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 md:gap-6 mb-8 md:mb-12">
            <div>
                <h1 className={`text-3xl sm:text-4xl md:text-5xl font-extrabold tracking-tight drop-shadow-sm ${isDark ? 'text-white' : 'text-slate-800'}`}>
                    D-Dash
                </h1>
            </div>
            <div className="md:text-right">
                <div className={`text-xl sm:text-2xl md:text-3xl font-bold tracking-tight mb-1 drop-shadow-sm ${isDark ? 'text-white' : 'text-slate-800'}`}>
                    {time.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </div>
                <p className={`text-sm sm:text-base font-medium ${isDark ? 'text-slate-400' : 'text-slate-500'}`}>
                    {time.toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric' })}
                </p>
            </div>
        </header>
    );
};
