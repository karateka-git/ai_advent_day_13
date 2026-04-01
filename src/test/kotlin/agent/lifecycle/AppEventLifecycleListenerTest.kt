package agent.lifecycle

import app.output.AppEvent
import app.output.AppEventSink
import kotlin.test.Test
import kotlin.test.assertEquals

class AppEventLifecycleListenerTest {
    @Test
    fun `publishes lifecycle callbacks as application events`() {
        val sink = RecordingAppEventSink()
        val listener = AppEventLifecycleListener(sink)
        val stats = ContextCompressionStats(tokensBefore = 10, tokensAfter = 7)

        listener.onModelWarmupStarted()
        listener.onModelWarmupFinished()
        listener.onModelRequestStarted()
        listener.onModelRequestFinished()
        listener.onContextCompressionStarted()
        listener.onContextCompressionFinished(stats)

        assertEquals(
            listOf(
                AppEvent.ModelWarmupStarted,
                AppEvent.ModelWarmupFinished,
                AppEvent.ModelRequestStarted,
                AppEvent.ModelRequestFinished,
                AppEvent.ContextCompressionStarted,
                AppEvent.ContextCompressionFinished(stats)
            ),
            sink.events
        )
    }
}

private class RecordingAppEventSink : AppEventSink {
    val events: MutableList<AppEvent> = mutableListOf()

    override fun emit(event: AppEvent) {
        events += event
    }
}
