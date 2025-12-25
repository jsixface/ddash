import {
    Video,
    Music,
    Cloud,
    Home,
    Activity,
    Wifi,
    Server,
    Database,
    Shield,
    LayoutGrid,
    Code,
    Terminal
} from 'lucide-react';
import type { AppData } from '../types/dashboard';

export const INITIAL_APPS: AppData[] = [
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
