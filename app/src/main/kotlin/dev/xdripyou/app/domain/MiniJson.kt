package dev.xdripyou.app.domain

/**
 * Lightweight, pure-Kotlin recursive-descent JSON parser (`Map<String, Any?>` / `List<Any?>` / primitives).
 * Works identically on Android runtime and JVM unit tests without depending on `org.json` Android stubs.
 */
object MiniJson {

    fun parse(json: String): Any? {
        val scanner = Scanner(json)
        scanner.skipWhitespace()
        if (scanner.isAtEnd) return null
        return scanner.parseValue()
    }

    @Suppress("UNCHECKED_CAST")
    fun asObject(value: Any?): Map<String, Any?>? = value as? Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun asArray(value: Any?): List<Any?>? = value as? List<Any?>

    fun objArray(obj: Map<String, Any?>, key: String): List<Map<String, Any?>> =
        asArray(obj[key])?.mapNotNull { asObject(it) } ?: emptyList()

    fun optString(obj: Map<String, Any?>, key: String): String? =
        obj[key]?.toString()?.takeIf { it != "null" }

    fun optDouble(obj: Map<String, Any?>, key: String): Double? = when (val v = obj[key]) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }

    fun optLong(obj: Map<String, Any?>, key: String): Long? = when (val v = obj[key]) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: v.toDoubleOrNull()?.toLong()
        else -> null
    }

    fun optBoolean(obj: Map<String, Any?>, key: String, default: Boolean = false): Boolean = when (val v = obj[key]) {
        is Boolean -> v
        is String -> v.equals("true", ignoreCase = true)
        else -> default
    }

    private class Scanner(private val src: String) {
        private var pos = 0

        val isAtEnd: Boolean get() = pos >= src.length

        fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        fun parseValue(): Any? {
            skipWhitespace()
            if (isAtEnd) return null
            return when (src[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                else -> parseNumber()
            }
        }

        private fun parseObject(): Map<String, Any?> {
            val map = linkedMapOf<String, Any?>()
            pos++ // '{'
            skipWhitespace()
            if (!isAtEnd && src[pos] == '}') {
                pos++
                return map
            }
            while (!isAtEnd) {
                skipWhitespace()
                if (isAtEnd || src[pos] != '"') break
                val key = parseString()
                skipWhitespace()
                if (!isAtEnd && src[pos] == ':') pos++
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                if (!isAtEnd && src[pos] == ',') {
                    pos++
                } else if (!isAtEnd && src[pos] == '}') {
                    pos++
                    break
                } else {
                    break
                }
            }
            return map
        }

        private fun parseArray(): List<Any?> {
            val list = mutableListOf<Any?>()
            pos++ // '['
            skipWhitespace()
            if (!isAtEnd && src[pos] == ']') {
                pos++
                return list
            }
            while (!isAtEnd) {
                list += parseValue()
                skipWhitespace()
                if (!isAtEnd && src[pos] == ',') {
                    pos++
                } else if (!isAtEnd && src[pos] == ']') {
                    pos++
                    break
                } else {
                    break
                }
            }
            return list
        }

        private fun parseString(): String {
            pos++ // opening '"'
            val sb = StringBuilder()
            while (pos < src.length) {
                val c = src[pos++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' && pos < src.length -> {
                        when (val esc = src[pos++]) {
                            '"', '\\', '/' -> sb.append(esc)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> if (pos + 4 <= src.length) {
                                val code = src.substring(pos, pos + 4).toIntOrNull(16)
                                if (code != null) sb.append(code.toChar())
                                pos += 4
                            }
                            else -> sb.append(esc)
                        }
                    }
                    else -> sb.append(c)
                }
            }
            return sb.toString()
        }

        private fun parseBoolean(): Boolean {
            return if (src.startsWith("true", pos)) {
                pos += 4
                true
            } else if (src.startsWith("false", pos)) {
                pos += 5
                false
            } else {
                pos++
                false
            }
        }

        private fun parseNull(): Any? {
            if (src.startsWith("null", pos)) pos += 4 else pos++
            return null
        }

        private fun parseNumber(): Number {
            val start = pos
            if (pos < src.length && src[pos] == '-') pos++
            var isFloat = false
            while (pos < src.length) {
                val c = src[pos]
                if (c in '0'..'9') {
                    pos++
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    if (c == '-' && pos == start) break
                    isFloat = true
                    pos++
                } else {
                    break
                }
            }
            val raw = src.substring(start, pos)
            if (raw.isEmpty()) return 0
            return if (isFloat) raw.toDoubleOrNull() ?: 0.0 else raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: 0.0
        }
    }
}
