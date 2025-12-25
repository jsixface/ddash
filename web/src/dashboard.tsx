import { useState, useEffect, useMemo } from 'react';
import * as Icons from 'lucide-react';

// --- Types ---
import type { AppData, ServerStats } from './types/dashboard';

// --- Mock Data ---
import { INITIAL_APPS } from './data/mockApps';

// --- Components ---
import { CommandPalette } from './components/dashboard/CommandPalette';
import { AppCard } from './components/dashboard/AppCard';
import { DashboardHeader } from './components/dashboard/DashboardHeader';
import { DashboardActionBar } from './components/dashboard/DashboardActionBar';

export default function NexusDashboard() {
    const [isCommandOpen, setIsCommandOpen] = useState(false);
    const [isDark, setIsDark] = useState(false);
    const [time, setTime] = useState(new Date());
    const [editMode, setEditMode] = useState(false);
    const [stats, setStats] = useState<ServerStats>({ cpu: "12", ram: "42", temp: "45" });
    const [apps, setApps] = useState<AppData[]>(import.meta.env.DEV ? INITIAL_APPS : []);

    useEffect(() => {
        if (!import.meta.env.DEV) {
            fetch('/api/apps')
                .then(res => res.json())
                .then(data => {
                    const processed = data.map((app: any) => ({
                        ...app,
                        icon: (Icons as any)[app.icon] || Icons.LayoutGrid
                    }));
                    setApps(processed);
                })
                .catch(err => console.error('Failed to fetch apps:', err));
        }
    }, []);

    // Clock Ticker
    useEffect(() => {
        const timer = setInterval(() => setTime(new Date()), 1000);
        return () => clearInterval(timer);
    }, []);

    // Keyboard Shortcuts
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
                e.preventDefault();
                setIsCommandOpen(true);
            }
        };
        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, []);

    // Simulate Live Server Stats
    useEffect(() => {
        const statTimer = setInterval(() => {
            setStats(prev => ({
                cpu: Math.min(100, Math.max(5, parseFloat(prev.cpu) + (Math.random() * 10 - 5))).toFixed(0),
                ram: Math.min(100, Math.max(20, parseFloat(prev.ram) + (Math.random() * 5 - 2.5))).toFixed(0),
                temp: Math.min(90, Math.max(30, parseFloat(prev.temp) + (Math.random() * 4 - 2))).toFixed(0),
            }));
        }, 2000);
        return () => clearInterval(statTimer);
    }, []);

    // Group Apps for Display
    const groupedApps = useMemo(() => {
        const groups: Record<string, AppData[]> = {};
        apps.forEach(app => {
            if (!groups[app.category]) groups[app.category] = [];
            groups[app.category].push(app);
        });
        return groups;
    }, [apps]);

    return (
        <div className={`min-h-screen font-sans overflow-x-hidden transition-colors duration-500 ${isDark ? 'bg-slate-950 text-slate-200 selection:bg-indigo-500/30 selection:text-indigo-200' : 'bg-slate-50 text-slate-600 selection:bg-violet-200 selection:text-violet-900'}`}>

            <CommandPalette
                isOpen={isCommandOpen}
                onClose={() => setIsCommandOpen(false)}
                apps={apps}
                isDark={isDark}
            />

            {/* Background Ambience */}
            <div className="fixed inset-0 z-0 pointer-events-none overflow-hidden transition-colors duration-700">
                {isDark ? (
                    <>
                        <div className="absolute top-[-10%] left-[-10%] w-[40%] h-[40%] bg-indigo-900/20 rounded-full blur-[120px] opacity-40 animate-pulse" />
                        <div className="absolute bottom-[-10%] right-[-10%] w-[40%] h-[40%] bg-blue-900/20 rounded-full blur-[120px] opacity-40" />
                    </>
                ) : (
                    <>
                        <div className="absolute top-[-10%] left-[-10%] w-[50%] h-[50%] bg-rose-200/40 rounded-full blur-[120px] mix-blend-multiply animate-pulse" />
                        <div className="absolute top-[20%] right-[-10%] w-[40%] h-[40%] bg-cyan-200/40 rounded-full blur-[120px] mix-blend-multiply" />
                        <div className="absolute bottom-[-10%] left-[20%] w-[40%] h-[40%] bg-violet-200/40 rounded-full blur-[120px] mix-blend-multiply" />
                    </>
                )}
            </div>

            <div className="relative z-10 max-w-7xl mx-auto px-6 py-8">

                <DashboardHeader
                    time={time}
                    stats={stats}
                    isDark={isDark}
                />

                <DashboardActionBar
                    onSearchClick={() => setIsCommandOpen(true)}
                    editMode={editMode}
                    setEditMode={setEditMode}
                    isDark={isDark}
                    setIsDark={setIsDark}
                />

                {/* Content Grid */}
                <div className="space-y-10 pb-20">
                    {Object.entries(groupedApps).map(([category, apps]) => (
                        <section key={category} className="animate-in fade-in slide-in-from-bottom-4 duration-500">
                            <div className="flex items-center gap-3 mb-5 px-1">
                                <h2 className={`text-sm font-bold uppercase tracking-widest ${isDark ? 'text-slate-400' : 'text-slate-400'}`}>
                                    {category}
                                </h2>
                                <div className={`h-px flex-1 bg-gradient-to-r ${isDark ? 'from-white/10' : 'from-slate-200'} to-transparent`} />
                            </div>

                            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
                                {apps.map((app) => (
                                    <AppCard key={app.id} app={app} editMode={editMode} isDark={isDark} />
                                ))}

                                {/* Add New Placeholder (Only in Edit Mode) */}
                                {editMode && (
                                    <button className={`aspect-square rounded-2xl border-2 border-dashed flex flex-col items-center justify-center transition-all group ${isDark ? 'border-white/10 hover:border-indigo-500/50 hover:bg-indigo-500/5 text-slate-500 hover:text-indigo-400' : 'border-slate-200 hover:border-violet-300 hover:bg-violet-50 text-slate-400 hover:text-violet-500'}`}>
                                        <div className={`p-3 rounded-full mb-2 transition-colors ${isDark ? 'bg-white/5 group-hover:bg-indigo-500/20' : 'bg-slate-100 group-hover:bg-violet-100'}`}>
                                            <Icons.MoreVertical size={24} />
                                        </div>
                                        <span className="text-xs font-bold">Add Service</span>
                                    </button>
                                )}
                            </div>
                        </section>
                    ))}
                </div>

            </div>
        </div>
    );
}
