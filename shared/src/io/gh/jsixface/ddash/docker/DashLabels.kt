package io.gh.jsixface.ddash.docker

enum class DashLabels(val label: String) {
    Name("ddash.name"),
    Category("ddash.category"),
    Route("ddash.route"),
    Icon("ddash.icon"),
    Port("ddash.port"),
    Enable("ddash.enable");

    companion object {
        fun fromLabel(label: String): DashLabels? {
            return entries.find { it.label == label }
        }
    }
}
