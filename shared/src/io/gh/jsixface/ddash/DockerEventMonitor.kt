package io.gh.jsixface.ddash

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.docker.DockerApiClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class DockerEventMonitor(
    private val dockerClient: DockerApiClient,
    private val onContainerEvent: suspend (String, String) -> Unit
) {
    private val logger = Logger.withTag("DockerEventMonitor")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start() {
        scope.launch {
            logger.i { "Starting Docker event monitoring..." }
            while (isActive) {
                try {
                    dockerClient.events().collectLatest { event ->
                        logger.d { "Docker event received: ${event.type} - ${event.action}" }
                        if (event.type == "container") {
                            if (event.action in listOf("start", "stop", "die", "destroy", "rename", "update")) {
                                logger.i { "Container event [${event.action}] for ${event.actor.id}. Triggering callback..." }
                                onContainerEvent(event.actor.id, event.action)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    logger.e(e) { "Error in Docker event monitoring. Retrying in 5 seconds..." }
                    delay(5000)
                }
            }
        }
    }

    fun stop() = scope.cancel()
}
