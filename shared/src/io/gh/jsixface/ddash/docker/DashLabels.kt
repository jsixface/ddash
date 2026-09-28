package io.gh.jsixface.ddash.docker

enum class DashLabels(val label: String) {
    Name("ddash.name"),
    Category("ddash.category"),
    Route("ddash.route"),
    Icon("ddash.icon"),
    Port("ddash.port"),
    Https("ddash.https"),
    Description("ddash.description"),
    Order("ddash.order"),
    Url("ddash.url"),
    Enable("ddash.enable");

    companion object {
        fun fromLabel(label: String): DashLabels? {
            return entries.find { it.label == label }
        }
    }
}

/**
 * Whether the container's route/URL should be https. The per-container `ddash.https` label wins over the global
 * default, so the Caddy route and the dashboard link always agree.
 */
fun Map<String, String>.isHttps(globalDefault: Boolean): Boolean =
    this[DashLabels.Https.label]?.toBoolean() ?: globalDefault
