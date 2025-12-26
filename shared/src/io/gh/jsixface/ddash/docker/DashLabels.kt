package io.gh.jsixface.ddash.docker

enum class DashLabels(val label: String) {
    Name ("ddash.name"),
    Category ("ddash.category"),
    Url ("ddash.url"),
    Icon ("ddash.icon"),
    Port ("ddash.port");

    companion object {
        fun fromLabel(label: String): DashLabels? {
            return entries.find { it.label == label }
        }
    }
}
