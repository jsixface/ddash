package io.gh.jsixface.ddash

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.api.AppData
import io.gh.jsixface.ddash.api.AppStatus
import io.gh.jsixface.ddash.api.HealthStatus
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.Toml
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.buffered

@Serializable
data class ExternalService(
    val name: String,
    val url: String,
    val category: String = "Uncategorized",
    val icon: String = "LayoutGrid",
    val description: String? = null
)

@Serializable
data class ExternalConfig(
    val services: List<ExternalService> = emptyList()
)

open class ExternalConfigService(private val configPath: String = "/config/services.toml") {
    private val logger = Logger.withTag("ExternalConfigService")

    open fun getExternalApps(): List<AppData> {
        val path = Path(configPath)
        if (!SystemFileSystem.exists(path)) {
            logger.d { "External config file not found at $configPath" }
            return emptyList()
        }

        return try {
            val content = readText(path)
            val config = Toml.decodeFromString(ExternalConfig.serializer(), content)
            config.services.map { service ->
                AppData(
                    id = "external-${service.name.hashCode()}",
                    name = service.name,
                    url = service.url,
                    category = service.category,
                    status = AppStatus.EXTERNAL,
                    icon = service.icon,
                    description = service.description,
                    health = HealthStatus.NONE
                )
            }
        } catch (e: Exception) {
            logger.e(e) { "Error reading or parsing external config file" }
            emptyList()
        }
    }

    private fun readText(path: Path): String {
        return SystemFileSystem.source(path).buffered().use { it.readString() }
    }
}
