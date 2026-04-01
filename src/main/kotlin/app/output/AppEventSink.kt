package app.output

/**
 * Принимает application-события, которые приложение хочет отобразить пользователю.
 */
interface AppEventSink {
    /**
     * Передаёт событие во внешний слой представления.
     */
    fun emit(event: AppEvent)
}
