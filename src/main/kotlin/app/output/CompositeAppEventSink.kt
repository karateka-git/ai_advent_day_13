package app.output

/**
 * Передаёт одно и то же application-событие в несколько sink'ов.
 */
class CompositeAppEventSink(
    private val sinks: List<AppEventSink>
) : AppEventSink {
    override fun emit(event: AppEvent) {
        sinks.forEach { it.emit(event) }
    }
}
