package com.flopster101.siliconplayer

enum class BrowserNameSortMode(
    val storageValue: String,
    val label: String
) {
    Natural("natural", "Natural"),
    Lexicographic("lexicographic", "Lexicographic");

    companion object {
        fun fromStorage(value: String?): BrowserNameSortMode {
            return entries.firstOrNull { it.storageValue == value } ?: Natural
        }
    }
}
