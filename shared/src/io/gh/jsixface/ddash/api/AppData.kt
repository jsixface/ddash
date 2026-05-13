package io.gh.jsixface.ddash.api

import kotlinx.serialization.Serializable

@Serializable
data class AppData(
    val id: String,
    val name: String,
    val url: String,
    val category: String,
    val status: AppStatus,
    val health: AppHealth = AppHealth.NONE,
    val icon: String = "LayoutGrid",
    val description: String? = null,
)

enum class AppStatus {
    RUNNING, EXITED, RESTARTING, CREATED, PAUSED, REMOVING, DEAD, EXTERNAL
}

enum class AppHealth {
    HEALTHY, UNHEALTHY, STARTING, NONE
}
