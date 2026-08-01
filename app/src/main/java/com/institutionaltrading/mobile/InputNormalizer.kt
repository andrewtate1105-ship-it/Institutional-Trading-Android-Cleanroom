package com.institutionaltrading.mobile

import java.util.Locale

object InputNormalizer {
    private val separators = Regex("[,;\\r\\n]+")

    fun parseList(value: String): Set<String> = value
        .split(separators)
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { item ->
            if (item.startsWith("http://", ignoreCase = true) || item.startsWith("https://", ignoreCase = true)) {
                item
            } else {
                item.uppercase(Locale.ROOT)
            }
        }
        .toCollection(linkedSetOf())
}
