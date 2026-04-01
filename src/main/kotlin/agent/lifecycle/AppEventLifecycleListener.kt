package agent.lifecycle

import app.output.AppEvent
import app.output.AppEventSink

/**
 * Адаптер между lifecycle-событиями приложения и внешним presentation-слоем
 * на основе нейтральных application-событий.
 */
class AppEventLifecycleListener(
    private val appEventSink: AppEventSink
) : AgentLifecycleListener {
    override fun onModelWarmupStarted() {
        appEventSink.emit(AppEvent.ModelWarmupStarted)
    }

    override fun onModelWarmupFinished() {
        appEventSink.emit(AppEvent.ModelWarmupFinished)
    }

    override fun onModelRequestStarted() {
        appEventSink.emit(AppEvent.ModelRequestStarted)
    }

    override fun onModelRequestFinished() {
        appEventSink.emit(AppEvent.ModelRequestFinished)
    }

    override fun onContextCompressionStarted() {
        appEventSink.emit(AppEvent.ContextCompressionStarted)
    }

    override fun onContextCompressionFinished(stats: ContextCompressionStats) {
        appEventSink.emit(AppEvent.ContextCompressionFinished(stats))
    }
}
