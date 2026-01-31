import {
    Activity,
    Cloud,
    Code,
    Database,
    Home,
    LayoutGrid,
    Music,
    Server,
    Shield,
    Terminal,
    Video,
    Wifi
} from 'lucide-react';
import type { AppData } from '../types/dashboard';

export const INITIAL_APPS: AppData[] = [
    { id: "1", name: "Plex Media Server", url: "https://plex.tv", category: "Media", icon: Video, status: "RUNNING", ping: "24ms" },
    { id: "2", name: "Spotify Connect", url: "https://spotify.com", category: "Media", icon: Music, status: "PAUSED", ping: "12ms" },
    { id: "3", name: "Sonarr", url: "https://sonarr.tv", category: "Media", icon: Cloud, status: "RUNNING", ping: "28ms" },
    { id: "4", name: "Radarr", url: "https://radarr.video", category: "Media", icon: Cloud, status: "RUNNING", ping: "29ms" },
    { id: "5", name: "Home Assistant", url: "https://home-assistant.io", category: "Smart Home", icon: Home, status: "RUNNING", ping: "4ms" },
    { id: "6", name: "Node-RED", url: "https://nodered.org", category: "Smart Home", icon: Activity, status: "RUNNING", ping: "5ms" },
    { id: "7", name: "Mosquitto MQTT", url: "https://mosquitto.org", category: "Smart Home", icon: Wifi, status: "RUNNING", ping: "2ms" },
    { id: "8", name: "Portainer", url: "https://portainer.io", category: "System", icon: Server, status: "RUNNING", ping: "1ms" },
    { id: "9", name: "Grafana", url: "https://grafana.com", category: "System", icon: Activity, status: "RUNNING", ping: "8ms" },
    { id: "10", name: "Prometheus", url: "https://prometheus.io", category: "System", icon: Database, status: "RESTARTING", ping: "150ms" },
    { id: "11", name: "Pi-hole", url: "https://pi-hole.net", category: "Network", icon: Shield, status: "RUNNING", ping: "1ms" },
    { id: "12", name: "Nginx Proxy Manager", url: "https://nginxproxymanager.com", category: "Network", icon: LayoutGrid, status: "RUNNING", ping: "3ms" },
    { id: "13", name: "VS Code Server", url: "https://code.visualstudio.com", category: "Development", icon: Code, status: "PAUSED", ping: "--" },
    { id: "14", name: "Jupyter Lab", url: "https://jupyter.org", category: "Development", icon: Terminal, status: "EXITED", ping: "--" },
];
