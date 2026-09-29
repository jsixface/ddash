package io.gh.jsixface.ddash

import io.gh.jsixface.ddash.docker.DockerApiClient
import io.gh.jsixface.ddash.docker.def.DockerActor
import io.gh.jsixface.ddash.docker.def.DockerEvent
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalAtomicApi::class)
class DockerEventMonitorTest {

    private fun event(action: String, type: String = "container") =
        DockerEvent(type = type, action = action, actor = DockerActor(id = "abc"))

    private class ScriptedDocker(private val streams: List<suspend kotlinx.coroutines.flow.FlowCollector<DockerEvent>.() -> Unit>) :
        DockerApiClient by FakeDockerApiClient() {
        val connections = AtomicInt(0)
        override fun events(): Flow<DockerEvent> {
            val script = streams.getOrElse(connections.fetchAndAdd(1)) { { kotlinx.coroutines.awaitCancellation() } }
            return flow { script() }
        }
    }

    @Test
    fun `burst of events triggers one pass`() = runBlocking {
        val passes = AtomicInt(0)
        val done = CompletableDeferred<Unit>()
        val docker = ScriptedDocker(listOf({
            repeat(5) { emit(event("start")) }
            emit(event("attach")) // not relevant
            emit(event("start", type = "image")) // not a container event
            kotlinx.coroutines.awaitCancellation()
        }))
        val monitor = DockerEventMonitor(docker, reconnectDelay = 10.milliseconds, debounce = 100.milliseconds) {
            passes.addAndFetch(1)
            done.complete(Unit)
        }

        monitor.start()
        withTimeout(5000) { done.await() }
        kotlinx.coroutines.delay(300)
        monitor.stop()

        assertTrue(passes.load() == 1, "expected a single coalesced pass but got ${passes.load()}")
    }

    @Test
    fun `reconnects and reconciles when the event stream ends`() = runBlocking {
        val passes = AtomicInt(0)
        val reconciled = CompletableDeferred<Unit>()
        // First connection ends immediately (Docker restarted); second one stays open.
        val docker = ScriptedDocker(listOf({ /* stream ends */ }, { kotlinx.coroutines.awaitCancellation() }))
        val monitor = DockerEventMonitor(docker, reconnectDelay = 20.milliseconds, debounce = 10.milliseconds) {
            passes.addAndFetch(1)
            reconciled.complete(Unit)
        }

        monitor.start()
        withTimeout(5000) { reconciled.await() }
        monitor.stop()

        assertTrue(docker.connections.load() >= 2, "should have reconnected")
        assertTrue(passes.load() >= 1, "should reconcile after reconnecting")
    }

    @Test
    fun `reconnects after a failure and keeps a failing callback from killing the monitor`() = runBlocking {
        val passes = AtomicInt(0)
        val second = CompletableDeferred<Unit>()
        val docker = ScriptedDocker(listOf(
            { error("connection reset") },
            {
                emit(event("die")) // coalesced with the reconnect pass, which fails
                kotlinx.coroutines.delay(300)
                emit(event("die")) // must still be handled after the failure
                kotlinx.coroutines.awaitCancellation()
            },
        ))
        val monitor = DockerEventMonitor(docker, reconnectDelay = 20.milliseconds, debounce = 10.milliseconds) {
            if (passes.addAndFetch(1) == 1) error("route pass failed")
            second.complete(Unit)
        }

        monitor.start()
        withTimeout(5000) { second.await() }
        monitor.stop()

        assertTrue(passes.load() >= 2)
    }
}
