## 2025-05-15 - [ddash.description label]
**Community Signal:** Users frequently ask for "description" or "subtitle" fields in self-hosted dashboards to provide context for services.
**Learning:** Adding a description field via a Docker label is a high-value, low-complexity feature that fits perfectly into DDash's minimalist, label-based architecture.
**Action:** Use the `Tooltip` component to display descriptions in the UI to keep the card layout clean while providing extra information on hover.

## 2025-05-22 - [Container health status badges]
**Community Signal:** Users prioritize "at-a-glance" health visibility over simple "running" status in dashboards.
**Learning:** Container health (healthy/unhealthy) is surfaced in the Docker API's `Status` field (e.g., "Up 2 hours (healthy)"). Parsing this string is much more efficient than performing a full `inspect` on every container, preserving DDash's lightweight performance.
**Action:** Always prefer parsing aggregated API fields for simple status indicators before reaching for more heavy-weight inspection APIs.
