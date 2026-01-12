# DDash (Docker-dash)

DDash is a lightweight, self-hosted dashboard and automatic reverse-proxy manager for Docker containers. It is designed to work seamlessly with [Caddy](https://caddyserver.com/), automatically discovering containers and configuring routes based on Docker labels.

## Features

- **Automatic Service Discovery**: Scans your Docker containers and automatically identifies those that should be exposed.
- **Caddy Integration**: Directly communicates with the Caddy Admin API to create and manage routes.
- **Centralized Dashboard**: Provides a clean web interface to view all your running applications, their status, and easy access via their configured routes.
- **Label-based Configuration**: Control everything through standard Docker labels—no need for complex configuration files.
- **Categorization**: Organize your apps into categories for a better overview.
- **Custom Icons**: Personalize your dashboard with custom icons for each application.

## How it Works

DDash connects to both the Docker socket and the Caddy Admin API. 
1. It monitors your Docker containers for specific labels.
2. When a container with `ddash.enable=true` and `ddash.route` is found, DDash checks if a corresponding route exists in Caddy.
3. If the route is missing, DDash automatically adds it to Caddy via its Admin API.
4. The web dashboard fetches the list of containers and displays them based on the `ddash` labels.

## Getting Started

### Prerequisites

- **Docker** and **Docker Compose**
- **Caddy**: Running in a container, with its **Admin API enabled** (this is usually the default, but ensure it's not disabled).
- **DNS Configuration**: A local network DNS with a **wildcard domain** (e.g., `*.local`) that resolves to your Caddy server's IP address. This allows DDash to dynamically route traffic to your containers using subdomains.

### Deployment Example

The recommended way to run DDash is as part of a Docker Compose stack where it shares the network namespace with your Caddy container.

```yaml
services:
  caddy:
    image: caddy:latest
    container_name: caddy
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile
      - caddy_data:/data
      - caddy_config:/config

  ddash:
    image: ghcr.io/jsixface/d-dash:latest
    container_name: ddash
    restart: unless-stopped
    network_mode: "service:caddy"
    environment:
      - DOCKER_SOCK=/var/run/docker.sock
      - CADDY_ADMIN_URL=http://localhost:2019
      - CADDY_AUTO_SAVE_CONFIG=true
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock:ro
    # Optional: If you want to access DDash dashboard through Caddy
    labels:
      - "ddash.enable=true"
      - "ddash.name=DDash"
      - "ddash.route=dash.local" # The URL will be http://dash.local
      - "ddash.category=System"
      - "ddash.icon=LayoutGrid" # Lucide icon name

volumes:
  caddy_data:
  caddy_config:
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DOCKER_SOCK` | Path to the Docker socket | `/var/run/docker.sock` |
| `PORT` | Port DDash listens on | `8080` |
| `LISTEN_ADDR` | Address DDash listens on | `0.0.0.0` |
| `CADDY_ADMIN_URL` | URL of the Caddy Admin API | `http://localhost:2019` |
| `CADDY_AUTO_SAVE_CONFIG` | Whether to tell Caddy to save its config after changes | `false` |
| `CADDY_SECURE_ROUTING` | If true, dashboard links use `https://`, otherwise `http://` | `false` |

### Docker Labels

Configure your applications by adding these labels to your containers:

| Label | Description | Required |
|-------|-------------|----------|
| `ddash.enable` | Set to `true` to show in dashboard and manage routing | Yes |
| `ddash.route` | The hostname for the application (e.g., `app.example.com`) | Yes |
| `ddash.name` | Display name in the dashboard | No (defaults to container name) |
| `ddash.category` | Grouping category in the dashboard | No (defaults to "Uncategorized") |
| `ddash.icon` | Icon name (supports Lucide icons) | No (defaults to "LayoutGrid") |
| `ddash.port` | The internal container port to proxy to | No (defaults to first exposed port or 80) |

## Example: Adding a new app

To add a new application (e.g., Whoami) to your dashboard and Caddy:

```yaml
services:
  whoami:
    image: traefik/whoami
    container_name: whoami
    labels:
      - "ddash.enable=true"
      - "ddash.name=Who Am I"
      - "ddash.route=whoami.local"
      - "ddash.category=Tools"
      - "ddash.icon=User"
```

Once this container starts, DDash will:
1. Detect the labels.
2. Instruct Caddy to route `whoami.local` to the `whoami` container.
3. Show "Who Am I" in the "Tools" section of your dashboard.

## License

This project is licensed under the GNU Public v3 – see the [LICENSE](LICENSE) file for details.
