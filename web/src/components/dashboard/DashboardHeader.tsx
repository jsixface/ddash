import React from 'react';
import { LogIn, LogOut } from 'lucide-react';
import type { SessionInfo } from '../../types/dashboard';

interface DashboardHeaderProps {
    time: Date;
    isDark: boolean;
    session: SessionInfo;
    onLogin: () => void;
    onLogout: () => void;
}

export const DashboardHeader: React.FC<DashboardHeaderProps> = ({ time, isDark, session, onLogin, onLogout }) => {
    const authButton = `flex items-center gap-2 px-3 py-1.5 md:px-4 md:py-2 rounded-lg md:rounded-xl border text-sm font-semibold transition-all shadow-sm ${isDark ? 'bg-white/5 hover:bg-white/10 border-white/10 text-slate-200' : 'bg-white/60 hover:bg-white/90 border-slate-200 text-slate-600 hover:text-violet-600'}`;

    return (
        <header className="relative flex flex-col md:flex-row justify-between items-start md:items-center gap-4 md:gap-6 mb-8 md:mb-12">
            <div>
                <h1 className={`text-3xl sm:text-4xl md:text-5xl font-extrabold tracking-tight drop-shadow-sm ${isDark ? 'text-white' : 'text-slate-800'}`}>
                    D-Dash
                </h1>
            </div>

            {/* Global login / logout, top right. Only shown when the server has OIDC configured. */}
            {session.authEnabled && (
                <div className="absolute top-0 right-0 flex items-center gap-3">
                    {session.user && (
                        <span className={`hidden sm:inline text-sm font-medium ${isDark ? 'text-slate-400' : 'text-slate-500'}`}>
                            {session.user.name}
                        </span>
                    )}
                    {session.user ? (
                        <button onClick={onLogout} className={authButton} title="Log out">
                            <LogOut size={16} />
                            <span>Log out</span>
                        </button>
                    ) : (
                        <button onClick={onLogin} className={authButton} title="Log in to view logs and start, stop or restart apps">
                            <LogIn size={16} />
                            <span>Log in</span>
                        </button>
                    )}
                </div>
            )}

            <div className={`md:text-right ${session.authEnabled ? 'md:pt-12' : ''}`}>
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
