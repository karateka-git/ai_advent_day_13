package agent.lifecycle

/**
 * Коллбеки уровня приложения для долгих этапов жизненного цикла, видимых в CLI.
 */
interface AgentLifecycleListener {
    /**
     * Вызывается перед прогревом локального токенизатора для выбранной модели.
     */
    fun onModelWarmupStarted()

    /**
     * Вызывается после завершения прогрева локального токенизатора.
     */
    fun onModelWarmupFinished()

    /**
     * Вызывается перед отправкой основного запроса в языковую модель.
     */
    fun onModelRequestStarted()

    /**
     * Вызывается после завершения основного запроса в языковую модель.
     */
    fun onModelRequestFinished()

    /**
     * Вызывается перед запуском сжатия истории диалога.
     */
    fun onContextCompressionStarted()

    /**
     * Вызывается после завершения сжатия истории диалога.
     *
     * @param stats статистика токенов до и после сжатия
     */
    fun onContextCompressionFinished(stats: ContextCompressionStats)
}

