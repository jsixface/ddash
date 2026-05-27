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
    { id: "1", name: "Plex Media Server", url: "https://plex.tv", category: "Media", icon: Video, status: "RUNNING", health: "HEALTHY", ping: "24ms", description: "Personal media streaming server", order: 1000 },
    { id: "2", name: "Spotify Connect", url: "https://spotify.com", category: "Media", icon: Music, status: "PAUSED", health: "NONE", ping: "12ms", description: "Connect and control Spotify devices", order: 1000 },
    { id: "3", name: "Sonarr", url: "https://sonarr.tv", category: "Media", icon: Cloud, status: "RUNNING", health: "HEALTHY", ping: "28ms", description: "Smart TV show PVR", order: 1000 },
    { id: "4", name: "Radarr", url: "https://radarr.video", category: "Media", icon: Cloud, status: "RUNNING", health: "UNHEALTHY", ping: "29ms", description: "Movie PVR for Usenet and BitTorrent", order: 1000 },
    { id: "5", name: "Home Assistant", url: "https://home-assistant.io", category: "Smart Home", icon: Home, status: "RUNNING", health: "HEALTHY", ping: "4ms", description: "Open source home automation", order: 1000 },
    { id: "6", name: "Node-RED", url: "https://nodered.org", category: "Smart Home", icon: Activity, status: "RUNNING", health: "STARTING", ping: "5ms", description: "Low-code programming for event-driven applications", order: 1000 },
    { id: "7", name: "Mosquitto MQTT", url: "https://mosquitto.org", category: "Smart Home", icon: Wifi, status: "RUNNING", health: "HEALTHY", ping: "2ms", description: "An open source MQTT broker", order: 1000 },
    { id: "8", name: "Portainer", url: "https://portainer.io", category: "System", icon: Server, status: "RUNNING", health: "HEALTHY", ping: "1ms", description: "Container management platform", order: 1000 },
    { id: "9", name: "Grafana", url: "https://grafana.com", category: "System", icon: Activity, status: "RUNNING", health: "HEALTHY", ping: "8ms", description: "The open observability platform", order: 1000 },
    { id: "10", name: "Prometheus", url: "https://prometheus.io", category: "System", icon: Database, status: "RESTARTING", health: "NONE", ping: "150ms", description: "Monitoring system & time series database", order: 1000 },
    { id: "11", name: "Pi-hole", url: "https://pi-hole.net", category: "Network", icon: Shield, status: "RUNNING", health: "HEALTHY", ping: "1ms", description: "A black hole for Internet advertisements", order: 1000 },
    { id: "12", name: "Nginx Proxy Manager", url: "https://nginxproxymanager.com", category: "Network", icon: LayoutGrid, status: "RUNNING", health: "HEALTHY", ping: "3ms", description: "Docker container for managing Nginx proxy hosts", order: 1000 },
    { id: "13", name: "VS Code Server", url: "https://code.visualstudio.com", category: "Development", icon: Code, status: "PAUSED", health: "NONE", ping: "--", description: "Run VS Code on a remote server", order: 1000 },
    { id: "14", name: "Jupyter Lab", url: "https://jupyter.org", category: "Development", icon: Terminal, status: "EXITED", health: "NONE", ping: "--", description: "Web-based interactive development environment", order: 1000 },
];
