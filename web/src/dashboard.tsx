import { useState, useEffect, useMemo } from 'react';
import * as Icons from 'lucide-react';

// --- Types ---
import type { AppData, SessionInfo } from './types/dashboard';

// --- Mock Data ---
import { INITIAL_APPS } from './data/mockApps';

// --- Components ---
import { CommandPalette } from './components/dashboard/CommandPalette';
import { AppCard } from './components/dashboard/AppCard';
import { DashboardHeader } from './components/dashboard/DashboardHeader';
import { DashboardActionBar } from './components/dashboard/DashboardActionBar';
import { LogViewer } from './components/dashboard/LogViewer';

function readAuthError(): string | null {
    const error = new URLSearchParams(window.location.search).get('auth_error');
    if (!error) return null;
    if (error === 'forbidden') return 'This account is not allowed to manage containers.';
    if (error === 'denied') return 'Login was cancelled.';
    return 'Login failed. Please try again.';
}

export default function NexusDashboard() {
    const [isCommandOpen, setIsCommandOpen] = useState(false);
    const [selectedAppForLogs, setSelectedAppForLogs] = useState<AppData | null>(null);
    const [isDark, setIsDark] = useState(false);
    const [time, setTime] = useState(new Date());

    const isMock = import.meta.env.MODE === 'development';
    const [apps, setApps] = useState<AppData[]>(isMock ? INITIAL_APPS : []);

    // In mock/dev mode everything is available; otherwise the server tells us what this visitor may do.
    const [session, setSession] = useState<SessionInfo>({ authEnabled: false, canManage: true });
    // The server redirects back with ?auth_error=... when a login fails.
    const [authError, setAuthError] = useState<string | null>(readAuthError);

    const fetchSession = () => {
        if (isMock) return;
        fetch('/api/session')
            .then(res => res.json())
            .then((data: SessionInfo) => setSession(data))
            .catch(err => console.error('Failed to fetch session:', err));
    };

    const login = () => {
        window.location.href = `/auth/login?returnTo=${encodeURIComponent(window.location.pathname)}`;
    };

    const logout = async () => {
        try {
            await fetch('/auth/logout', { method: 'POST' });
        } catch (err) {
            console.error('Failed to log out:', err);
        }
        fetchSession();
    };

    const fetchApps = () => {
        if (!isMock) {
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
    };

    useEffect(() => {
        fetchApps();
        fetchSession();

        // Drop ?auth_error from the address bar now that it has been shown.
        const params = new URLSearchParams(window.location.search);
        if (params.has('auth_error')) {
            params.delete('auth_error');
            const query = params.toString();
            window.history.replaceState({}, '', window.location.pathname + (query ? `?${query}` : ''));
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

            <LogViewer
                app={selectedAppForLogs}
                isOpen={!!selectedAppForLogs}
                onClose={() => setSelectedAppForLogs(null)}
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

            <div className="relative z-10 max-w-7xl mx-auto px-4 md:px-6 py-6 md:py-8">

                <DashboardHeader
                    time={time}
                    isDark={isDark}
                />

                <DashboardActionBar
                    onSearchClick={() => setIsCommandOpen(true)}
                    isDark={isDark}
                    setIsDark={setIsDark}
                    session={session}
                    onLogin={login}
                    onLogout={logout}
                />

                {authError && (
                    <div
                        role="alert"
                        className={`mb-6 flex items-center justify-between gap-4 rounded-xl border px-4 py-3 text-sm ${isDark ? 'bg-rose-950/40 border-rose-500/30 text-rose-200' : 'bg-rose-50 border-rose-200 text-rose-700'}`}
                    >
                        <span>{authError}</span>
                        <button onClick={() => setAuthError(null)} className="font-semibold underline">Dismiss</button>
                    </div>
                )}

                {/* Content Grid */}
                <div className="space-y-8 md:space-y-10 pb-28 md:pb-20">
                    {Object.entries(groupedApps).map(([category, apps]) => (
                        <section key={category} className="animate-in fade-in slide-in-from-bottom-4 duration-500">
                            <div className="flex items-center gap-2 md:gap-3 mb-4 md:mb-5 px-1">
                                <h2 className={`text-xs md:text-sm font-bold uppercase tracking-widest ${isDark ? 'text-slate-400' : 'text-slate-400'}`}>
                                    {category}
                                </h2>
                                <div className={`h-px flex-1 bg-gradient-to-r ${isDark ? 'from-white/10' : 'from-slate-200'} to-transparent`} />
                            </div>

                            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-3 md:gap-4">
                                {apps.map((app) => (
                                    <AppCard
                                        key={app.id}
                                        app={app}
                                        isDark={isDark}
                                        onViewLogs={(app) => setSelectedAppForLogs(app)}
                                        onActionSuccess={fetchApps}
                                        session={session}
                                        onLogin={login}
                                        onUnauthorized={fetchSession}
                                    />
                                ))}
                            </div>
                        </section>
                    ))}
                </div>

            </div>
        </div>
    );
}
