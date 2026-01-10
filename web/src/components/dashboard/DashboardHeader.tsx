import React from 'react';
import { Cpu, HardDrive, Activity } from 'lucide-react';
import { ServerStat } from './ServerStat';
import type { ServerStats } from '../../types/dashboard';

interface DashboardHeaderProps {
    time: Date;
    stats: ServerStats;
    isDark: boolean;
}

export const DashboardHeader: React.FC<DashboardHeaderProps> = ({ time, stats, isDark }) => {
    return (
        <header className="flex flex-col md:flex-row justify-between items-start md:items-end gap-4 md:gap-6 mb-8 md:mb-12">
            <div>
                <h1 className={`text-3xl sm:text-4xl md:text-5xl font-bold tracking-tight mb-1 md:mb-2 drop-shadow-sm ${isDark ? 'text-white' : 'text-slate-800'}`}>
                    {time.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </h1>
                <p className={`text-sm sm:text-base md:text-lg font-medium ${isDark ? 'text-slate-400' : 'text-slate-500'}`}>
                    {time.toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric' })}
                </p>
            </div>

            <div className="flex flex-wrap gap-2 sm:gap-3 md:gap-4 w-full md:w-auto">
                <ServerStat
                    icon={Cpu}
                    label="CPU Load"
                    value={stats.cpu}
                    unit="%"
                    color=""
                    iconColor={isDark ? "text-emerald-400" : "text-emerald-500"}
                    tooltip="Total CPU utilization across all cores"
                    isDark={isDark}
                />
                <ServerStat
                    icon={HardDrive}
                    label="RAM Usage"
                    value={stats.ram}
                    unit="%"
                    color=""
                    iconColor={isDark ? "text-blue-400" : "text-sky-500"}
                    tooltip="Memory usage (16GB Total)"
                    isDark={isDark}
                />
                <ServerStat
                    icon={Activity}
                    label="Core Temp"
                    value={stats.temp}
                    unit="°C"
                    color=""
                    iconColor={isDark ? "text-amber-400" : "text-rose-500"}
                    tooltip="Average CPU package temperature"
                    isDark={isDark}
                />
            </div>
        </header>
    );
};
