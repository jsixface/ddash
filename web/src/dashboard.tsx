import React, { useState, useEffect, useMemo, useRef, type ReactNode } from 'react';
import {
    Search,
    Cpu,
    HardDrive,
    Activity,
    Settings,
    MoreVertical,
    Wifi,
    Server,
    Terminal,
    Database,
    LayoutGrid,
    Music,
    Video,
    Shield,
    Home,
    Code,
    Cloud,
    ExternalLink,
    Edit3,
    CheckCircle,
    X,
    Power,
    RefreshCw,
    FileText,
    Pin,
    Moon,
    Sun,
    type LucideIcon
} from 'lucide-react';

// --- Types & Interfaces ---

type AppStatus = 'running' | 'idle' | 'warning' | 'stopped';

interface AppData {
    id: number;
    name: string;
    url: string;
    category: string;
    icon: LucideIcon;
    status: AppStatus;
    ping: string;
}

interface MenuItem {
    label: string;
    icon: LucideIcon;
    action: () => void;
    variant?: 'default' | 'danger';
}

interface ServerStats {
    cpu: string;
    ram: string;
    temp: string;
}

// --- Mock Data ---
const INITIAL_APPS: AppData[] = [
    { id: 1, name: "Plex Media Server", url: "#", category: "Media", icon: Video, status: "running", ping: "24ms" },
    { id: 2, name: "Spotify Connect", url: "#", category: "Media", icon: Music, status: "idle", ping: "12ms" },
    { id: 3, name: "Sonarr", url: "#", category: "Media", icon: Cloud, status: "running", ping: "28ms" },
    { id: 4, name: "Radarr", url: "#", category: "Media", icon: Cloud, status: "running", ping: "29ms" },
    { id: 5, name: "Home Assistant", url: "#", category: "Smart Home", icon: Home, status: "running", ping: "4ms" },
    { id: 6, name: "Node-RED", url: "#", category: "Smart Home", icon: Activity, status: "running", ping: "5ms" },
    { id: 7, name: "Mosquitto MQTT", url: "#", category: "Smart Home", icon: Wifi, status: "running", ping: "2ms" },
    { id: 8, name: "Portainer", url: "#", category: "System", icon: Server, status: "running", ping: "1ms" },
    { id: 9, name: "Grafana", url: "#", category: "System", icon: Activity, status: "running", ping: "8ms" },
    { id: 10, name: "Prometheus", url: "#", category: "System", icon: Database, status: "warning", ping: "150ms" },
    { id: 11, name: "Pi-hole", url: "#", category: "Network", icon: Shield, status: "running", ping: "1ms" },
    { id: 12, name: "Nginx Proxy Manager", url: "#", category: "Network", icon: LayoutGrid, status: "running", ping: "3ms" },
    { id: 13, name: "VS Code Server", url: "#", category: "Development", icon: Code, status: "idle", ping: "--" },
    { id: 14, name: "Jupyter Lab", url: "#", category: "Development", icon: Terminal, status: "stopped", ping: "--" },
];

// --- Primitives ---

interface TooltipProps {
    children: ReactNode;
    content: string;
    isDark: boolean;
}

const Tooltip: React.FC<TooltipProps> = ({ children, content, isDark }) => {
    const [isVisible, setIsVisible] = useState(false);

    return (
        <div
            className="relative flex items-center"
            onMouseEnter={() => setIsVisible(true)}
            onMouseLeave={() => setIsVisible(false)}
        >
            {children}
            {isVisible && (
                <div className={`
          absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-3 py-1.5 
          ${isDark ? 'bg-slate-800 border-white/10 text-slate-200' : 'bg-white/90 border-white/50 text-slate-600 shadow-rose-900/5'}
          border text-xs rounded-lg shadow-xl whitespace-nowrap z-50 animate-in fade-in zoom-in-95 duration-200 backdrop-blur-md font-medium
        `}>
                    {content}
                    <div className={`absolute bottom-[-4px] left-1/2 -translate-x-1/2 w-2 h-2 border-r border-b rotate-45 ${isDark ? 'bg-slate-800 border-white/10' : 'bg-white/90 border-white/50'}`} />
                </div>
            )}
        </div>
    );
};

interface ContextMenuProps {
    children: ReactNode;
    menuItems: MenuItem[];
    isDark: boolean;
}

const ContextMenu: React.FC<ContextMenuProps> = ({ children, menuItems, isDark }) => {
    const [visible, setVisible] = useState(false);
    const [position, setPosition] = useState({ x: 0, y: 0 });
    const menuRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
                setVisible(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const handleContextMenu = (e: React.MouseEvent) => {
        e.preventDefault();
        setVisible(true);
        setPosition({ x: e.pageX, y: e.pageY });
    };

    return (
        <div onContextMenu={handleContextMenu}>
            {children}
            {visible && (
                <div
                    ref={menuRef}
                    style={{ top: position.y, left: position.x }}
                    className={`
            fixed z-50 w-48 backdrop-blur-xl border rounded-xl shadow-2xl p-1.5 animate-in fade-in zoom-in-95 duration-100
            ${isDark ? 'bg-slate-900/95 border-white/10 shadow-black/50' : 'bg-white/80 border-white/50 shadow-slate-200/50'}
          `}
                >
                    {menuItems.map((item, idx) => (
                        <button
                            key={idx}
                            onClick={(e) => {
                                e.stopPropagation();
                                item.action();
                                setVisible(false);
                            }}
                            className={`
                w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm text-left transition-colors
                ${item.variant === 'danger'
                                ? (isDark ? 'text-red-400 hover:bg-red-500/10' : 'text-rose-500 hover:bg-rose-50')
                                : (isDark ? 'text-slate-300 hover:bg-white/10 hover:text-white' : 'text-slate-600 hover:bg-violet-50 hover:text-violet-600')}
              `}
                        >
                            <item.icon size={14} />
                            {item.label}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
};

interface CommandPaletteProps {
    isOpen: boolean;
    onClose: () => void;
    apps: AppData[];
    isDark: boolean;
}

const CommandPalette: React.FC<CommandPaletteProps> = ({ isOpen, onClose, apps, isDark }) => {
    const [query, setQuery] = useState("");
    const inputRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        if (isOpen) {
            setTimeout(() => inputRef.current?.focus(), 50);
            document.body.style.overflow = 'hidden';
        } else {
            document.body.style.overflow = 'unset';
            setQuery("");
        }
        return () => { document.body.style.overflow = 'unset'; };
    }, [isOpen]);

    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === 'Escape' && isOpen) onClose();
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, onClose]);

    const filteredApps = useMemo(() => {
        if (!query) return apps.slice(0, 5);
        return apps.filter(app =>
            app.name.toLowerCase().includes(query.toLowerCase()) ||
            app.category.toLowerCase().includes(query.toLowerCase())
        );
    }, [query, apps]);

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-[100] flex items-start justify-center pt-[15vh] px-4">
            <div
                className={`absolute inset-0 backdrop-blur-sm animate-in fade-in duration-200 ${isDark ? 'bg-slate-950/60' : 'bg-slate-200/40'}`}
                onClick={onClose}
            />

            <div className={`
        relative w-full max-w-2xl backdrop-blur-2xl border rounded-2xl shadow-2xl overflow-hidden animate-in zoom-in-95 fade-in slide-in-from-bottom-2 duration-200
        ${isDark ? 'bg-slate-900 border-white/10 shadow-black/50' : 'bg-white/70 border-white/60 shadow-violet-500/10'}
      `}>
                <div className={`flex items-center px-4 py-4 border-b ${isDark ? 'border-white/5' : 'border-slate-100/50'}`}>
                    <Search className={`mr-3 ${isDark ? 'text-slate-500' : 'text-slate-400'}`} size={20} />
                    <input
                        ref={inputRef}
                        type="text"
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                        placeholder="Search apps..."
                        className={`flex-1 bg-transparent border-none text-lg focus:outline-none ${isDark ? 'text-white placeholder-slate-500' : 'text-slate-700 placeholder-slate-400'}`}
                    />
                    <div className="flex gap-2">
                        <kbd className={`hidden sm:inline-flex items-center gap-1 px-2 py-1 rounded-md text-xs font-sans border ${isDark ? 'bg-white/5 border-white/10 text-slate-400' : 'bg-slate-100 border-slate-200 text-slate-500'}`}>
                            ESC
                        </kbd>
                    </div>
                </div>

                <div className="max-h-[60vh] overflow-y-auto p-2">
                    {filteredApps.length === 0 ? (
                        <div className={`py-12 text-center ${isDark ? 'text-slate-600' : 'text-slate-500'}`}>
                            <p>No results found.</p>
                        </div>
                    ) : (
                        <>
                            {!query && <div className={`px-3 py-2 text-xs font-medium uppercase tracking-wider ${isDark ? 'text-slate-500' : 'text-slate-400'}`}>Suggested</div>}
                            {filteredApps.map((app) => (
                                <button
                                    key={app.id}
                                    className={`
                    w-full flex items-center gap-3 p-3 rounded-xl group transition-colors text-left
                    ${isDark ? 'hover:bg-white/5' : 'hover:bg-violet-50/50'}
                  `}
                                    onClick={() => {
                                        console.log(`Launching ${app.name}`);
                                        onClose();
                                    }}
                                >
                                    <div className={`
                    p-2 rounded-lg transition-colors border
                    ${isDark
                                        ? 'bg-slate-800 text-slate-400 group-hover:text-indigo-400 group-hover:bg-indigo-500/10 border-transparent'
                                        : 'bg-white text-slate-400 group-hover:text-violet-500 group-hover:border-violet-200 border-slate-100 shadow-sm'}
                  `}>
                                        <app.icon size={18} />
                                    </div>
                                    <div className="flex-1">
                                        <div className={`font-medium ${isDark ? 'text-slate-200' : 'text-slate-700'}`}>{app.name}</div>
                                        <div className={`text-xs ${isDark ? 'text-slate-500' : 'text-slate-400'}`}>{app.category} • {app.status}</div>
                                    </div>
                                    <ExternalLink size={14} className={`opacity-0 group-hover:opacity-100 transition-all ${isDark ? 'text-slate-600 group-hover:text-slate-400' : 'text-slate-400 group-hover:text-violet-400'}`} />
                                </button>
                            ))}
                        </>
                    )}
                </div>
            </div>
        </div>
    );
};

// --- Components ---

const StatusDot: React.FC<{ status: AppStatus }> = ({ status }) => {
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

const ServerStat: React.FC<ServerStatProps> = ({ icon: Icon, label, value, unit, tooltip, iconColor, isDark }) => (
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

interface AppCardProps {
    app: AppData;
    editMode: boolean;
    isDark: boolean;
}

const AppCard: React.FC<AppCardProps> = ({ app, editMode, isDark }) => {
    const Icon = app.icon;

    const menuItems: MenuItem[] = [
        { label: "Launch App", icon: ExternalLink, action: () => console.log("Launch") },
        { label: "Pin to Home", icon: Pin, action: () => console.log("Pin") },
        { label: "View Logs", icon: FileText, action: () => console.log("Logs") },
        { label: "Restart Container", icon: RefreshCw, action: () => console.log("Restart") },
        { label: "Stop Service", icon: Power, action: () => console.log("Stop"), variant: 'danger' },
    ];

    return (
        <ContextMenu menuItems={menuItems} isDark={isDark}>
            <div className={`
        relative group flex flex-col items-center justify-center 
        aspect-square p-4 rounded-2xl backdrop-blur-sm border
        transition-all duration-300 ease-out
        ${editMode ? 'animate-pulse cursor-move' : 'hover:-translate-y-1 cursor-pointer'}
        ${isDark
                ? 'bg-gradient-to-br from-white/5 to-white/0 border-white/5 hover:border-white/20 hover:bg-white/10'
                : 'bg-white/60 border-slate-200/60 shadow-sm hover:shadow-md hover:border-violet-200 hover:bg-white/80'}
      `}>
                {editMode && (
                    <div className="absolute top-2 right-2 p-1 bg-rose-100 text-rose-500 rounded-full hover:bg-rose-500 hover:text-white transition-colors z-20">
                        <X size={12} />
                    </div>
                )}

                {/* Hover Glow Effect */}
                <div className={`
          absolute inset-0 rounded-2xl transition-all duration-500
          bg-gradient-to-br 
          ${isDark
                    ? 'from-indigo-500/0 via-indigo-500/0 to-indigo-500/0 group-hover:to-indigo-500/10'
                    : 'from-violet-200/0 via-violet-200/0 to-violet-200/0 group-hover:to-violet-200/20'}
        `} />

                <div className={`
          relative mb-3 p-3 rounded-xl transition-transform duration-300 shadow-sm
          ${isDark
                    ? 'bg-slate-900/50 shadow-lg group-hover:shadow-indigo-500/20'
                    : 'bg-white shadow-slate-200/50 group-hover:scale-110 border border-slate-100'}
        `}>
                    <Icon size={32} className={`transition-colors ${isDark ? 'text-slate-200 group-hover:text-indigo-300' : 'text-slate-500 group-hover:text-violet-500'}`} />
                </div>

                <h3 className={`text-sm font-semibold text-center line-clamp-1 transition-colors ${isDark ? 'text-slate-200 group-hover:text-white' : 'text-slate-700 group-hover:text-violet-700'}`}>
                    {app.name}
                </h3>

                <div className="mt-2 flex items-center gap-2">
                    <StatusDot status={app.status} />
                    <span className={`text-[10px] font-mono font-medium ${isDark ? (app.status === 'stopped' ? 'text-slate-500' : 'text-slate-400') : (app.status === 'stopped' ? 'text-slate-400' : 'text-slate-500')}`}>
            {app.status}
          </span>
                </div>
            </div>
        </ContextMenu>
    );
};

// --- Main Application ---

export default function NexusDashboard() {
    const [isCommandOpen, setIsCommandOpen] = useState(false);
    const [isDark, setIsDark] = useState(false);
    const [time, setTime] = useState(new Date());
    const [editMode, setEditMode] = useState(false);
    const [stats, setStats] = useState<ServerStats>({ cpu: "12", ram: "42", temp: "45" });

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
        INITIAL_APPS.forEach(app => {
            if (!groups[app.category]) groups[app.category] = [];
            groups[app.category].push(app);
        });
        return groups;
    }, []);

    return (
        <div className={`min-h-screen font-sans overflow-x-hidden transition-colors duration-500 ${isDark ? 'bg-slate-950 text-slate-200 selection:bg-indigo-500/30 selection:text-indigo-200' : 'bg-slate-50 text-slate-600 selection:bg-violet-200 selection:text-violet-900'}`}>

            <CommandPalette
                isOpen={isCommandOpen}
                onClose={() => setIsCommandOpen(false)}
                apps={INITIAL_APPS}
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

                {/* Header Section */}
                <header className="flex flex-col md:flex-row justify-between items-start md:items-end gap-6 mb-12">
                    <div>
                        <h1 className={`text-4xl md:text-5xl font-bold tracking-tight mb-2 drop-shadow-sm ${isDark ? 'text-white' : 'text-slate-800'}`}>
                            {time.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                        </h1>
                        <p className={`text-lg font-medium ${isDark ? 'text-slate-400' : 'text-slate-500'}`}>
                            {time.toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric' })}
                        </p>
                    </div>

                    <div className="flex flex-wrap gap-4">
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

                {/* Action Bar */}
                <div className="sticky top-6 z-40 mb-12">
                    <div className={`flex items-center justify-between gap-4 p-2 backdrop-blur-xl border rounded-2xl shadow-xl max-w-4xl mx-auto transition-colors duration-300 ${isDark ? 'bg-slate-900/80 border-white/10' : 'bg-white/60 border-white/60 shadow-slate-200/50'}`}>

                        {/* Fake Search Trigger */}
                        <button
                            onClick={() => setIsCommandOpen(true)}
                            className={`flex-1 flex items-center gap-3 px-4 py-3 border rounded-xl transition-all group text-left shadow-sm ${isDark ? 'bg-white/5 hover:bg-white/10 border-white/5 hover:border-white/10' : 'bg-white/40 hover:bg-white/60 border-white/40 hover:border-white/60'}`}
                        >
                            <Search className={`${isDark ? 'text-slate-400 group-hover:text-slate-200' : 'text-slate-400 group-hover:text-slate-600'}`} size={20} />
                            <span className={`font-medium ${isDark ? 'text-slate-400 group-hover:text-slate-300' : 'text-slate-400 group-hover:text-slate-600'}`}>Quick launch...</span>
                            <div className="ml-auto flex gap-1">
                                <kbd className={`hidden sm:inline-flex items-center gap-1 px-2 py-1 rounded-md text-xs font-mono border ${isDark ? 'bg-slate-800 text-slate-400 border-white/10' : 'bg-slate-100 text-slate-500 border-slate-200'}`}>
                                    ⌘K
                                </kbd>
                            </div>
                        </button>

                        <div className={`w-px h-8 mx-1 ${isDark ? 'bg-white/10' : 'bg-slate-200'}`} />

                        {/* Tools */}
                        <div className="flex items-center gap-2 pr-2">
                            <Tooltip content={editMode ? "Save Layout" : "Edit Layout"} isDark={isDark}>
                                <button
                                    onClick={() => setEditMode(!editMode)}
                                    className={`p-3 rounded-xl transition-all shadow-sm ${editMode ? 'bg-indigo-500 text-white shadow-lg shadow-indigo-500/25' : (isDark ? 'hover:bg-white/10 text-slate-400 hover:text-white' : 'bg-white/40 hover:bg-white/80 border border-white/40 hover:border-white/60 text-slate-400 hover:text-violet-500')}`}
                                >
                                    {editMode ? <CheckCircle size={20} /> : <Edit3 size={20} />}
                                </button>
                            </Tooltip>

                            <Tooltip content={isDark ? "Light Mode" : "Dark Mode"} isDark={isDark}>
                                <button
                                    onClick={() => setIsDark(!isDark)}
                                    className={`p-3 rounded-xl transition-all shadow-sm ${isDark ? 'hover:bg-white/10 text-slate-400 hover:text-amber-300' : 'bg-white/40 hover:bg-white/80 border border-white/40 hover:border-white/60 text-slate-400 hover:text-violet-500'}`}
                                >
                                    {isDark ? <Sun size={20} /> : <Moon size={20} />}
                                </button>
                            </Tooltip>

                            <Tooltip content="System Settings" isDark={isDark}>
                                <button className={`p-3 rounded-xl transition-colors shadow-sm ${isDark ? 'hover:bg-white/10 text-slate-400 hover:text-white' : 'bg-white/40 hover:bg-white/80 border border-white/40 hover:border-white/60 text-slate-400 hover:text-slate-600'}`}>
                                    <Settings size={20} />
                                </button>
                            </Tooltip>
                        </div>
                    </div>
                </div>

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
                                            <MoreVertical size={24} />
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
