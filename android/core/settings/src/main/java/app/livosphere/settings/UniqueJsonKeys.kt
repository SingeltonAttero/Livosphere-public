package app.livosphere.settings

import kotlinx.serialization.json.Json

/** Scan object member names before JsonObject can discard duplicate values. The regular serializer
 * still validates JSON syntax/types. Keys are decoded, so escaped equivalents collide as well.
 */
internal object UniqueJsonKeys {
    fun check(text: String) { Scanner(text).check() }

    private class Scanner(private val text: String) {
        private var index = 0
        fun check() { value(0); whitespace(); require(index == text.length) }
        private fun whitespace() { while (index < text.length && text[index] in " \t\r\n") index++ }
        private fun take(char: Char) { whitespace(); require(index < text.length && text[index++] == char) }
        private fun peek(): Char { whitespace(); require(index < text.length); return text[index] }
        private fun value(depth: Int) {
            require(depth <= 128) // Malformed input must not exhaust the process stack.
            when (peek()) {
                '{' -> {
                    take('{')
                    val keys = mutableSetOf<String>()
                    if (peek() != '}') {
                        while (true) {
                            val key = Json.decodeFromString<String>(string())
                            require(keys.add(key)) { "Duplicate settings member" }
                            take(':'); value(depth + 1)
                            if (peek() != ',') break
                            take(',')
                        }
                    }
                    take('}')
                }
                '[' -> {
                    take('[')
                    if (peek() != ']') {
                        while (true) {
                            value(depth + 1)
                            if (peek() != ',') break
                            take(',')
                        }
                    }
                    take(']')
                }
                '"' -> string()
                else -> {
                    val start = index
                    while (index < text.length && text[index] !in ",]} \t\r\n") index++
                    require(index > start)
                }
            }
        }
        private fun string(): String {
            whitespace()
            val start = index
            take('"')
            while (index < text.length) {
                when (text[index++]) {
                    '"' -> return text.substring(start, index)
                    '\\' -> { require(index < text.length); index++ }
                }
            }
            throw IllegalArgumentException("Unterminated settings string")
        }
    }
}
