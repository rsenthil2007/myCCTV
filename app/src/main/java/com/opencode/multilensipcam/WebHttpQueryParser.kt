package com.opencode.multilensipcam

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object WebHttpQueryParser {
    fun parse(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&")
            .mapNotNull { pair ->
                val key = pair.substringBefore("=", "")
                if (key.isBlank()) return@mapNotNull null
                val value = pair.substringAfter("=", "")
                key to URLDecoder.decode(value, StandardCharsets.UTF_8.name())
            }
            .toMap()
    }
}
