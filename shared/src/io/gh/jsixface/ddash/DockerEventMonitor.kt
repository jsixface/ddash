package io.gh.jsixface.ddash

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.docker.DockerApiClient
import io.gh.jsixface.ddash.docker.UnixSocketDockerApiClient
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Watches Docker container events and calls [onContainersChanged] when something relevant happens.
 *
 * - Bursts of events (a compose stack starting) are coalesced: events arriving during the [debounce] window are
 *   covered by the pass that follows it, and events arriving while a pass runs collapse into a single follow-up pass.
 *   A running pass is never cancelled half way.
 * - If the event stream ends or fails (Docker restarted), it reconnects after [reconnectDelay] and triggers a pass,
 *   since events emitted while disconnected were missed.
 */
class DockerEventMonitor(
    private val dockerClient: DockerApiClient,
    private val reconnectDelay: Duration = 5.seconds,
    private val debounce: Duration = 500.milliseconds,
    private val onContainersChanged: suspend () -> Unit,
) {
    private val logger = Logger.withTag("DockerEventMonitor")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start() {
        val trigger = Channel<Unit>(Channel.CONFLATED)

        scope.launch {
            for (ignored in trigger) {
                delay(debounce)
                // Events that arrived while we waited are covered by the pass we are about to run.
                trigger.tryReceive()
                try {
                    onContainersChanged()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    logger.e(e) { "Error handling container change" }
                }
            }
        }

        scope.launch {
            logger.i { "Starting Docker event monitoring..." }
            var firstConnection = true
            while (isActive) {
                try {
                    // Anything that happened while we were disconnected was missed, so reconcile on reconnect.
                    if (!firstConnection) trigger.trySend(Unit)
                    firstConnection = false
                    dockerClient.events().collect { event ->
                        logger.d { "Docker event received: ${event.type} - ${event.action}" }
                        if (event.type == "container" && event.action in UnixSocketDockerApiClient.CONTAINER_EVENT_ACTIONS) {
                            logger.i { "Container event [${event.action}] for ${event.actor.id}." }
                            trigger.trySend(Unit)
                        }
                    }
                    logger.w { "Docker event stream ended. Reconnecting in $reconnectDelay..." }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    logger.e(e) { "Error in Docker event monitoring. Reconnecting in $reconnectDelay..." }
                }
                delay(reconnectDelay)
            }
        }
    }

    fun stop() = scope.cancel()
}
