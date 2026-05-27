package io.gh.jsixface.ddash.api

import kotlinx.serialization.Serializable

@Serializable
data class AppData(
    val id: String,
    val name: String,
    val url: String,
    val category: String,
    val status: AppStatus,
    val icon: String = "LayoutGrid",
    val description: String? = null,
    val order: Int = Int.MAX_VALUE,
    val health: HealthStatus = HealthStatus.NONE,
)

enum class AppStatus {
    RUNNING, EXITED, RESTARTING, CREATED, PAUSED, REMOVING, DEAD, EXTERNAL
}

enum class HealthStatus {
    HEALTHY, UNHEALTHY, STARTING, NONE
}
