package agent.format

interface ResponseFormat<T> {
    val formatInstruction: String

    /**
     * Преобразует сырой текстовый ответ модели в целевой тип результата.
     *
     * @param rawResponse исходный текст ответа модели
     * @return распарсенный результат в формате, ожидаемом агентом
     */
    fun parse(rawResponse: String): T
}
