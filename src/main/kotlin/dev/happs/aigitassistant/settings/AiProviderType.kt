package dev.happs.aigitassistant.settings

/**
 * Available assistant provider modes.
 */
enum class AiProviderType {
    DETERMINISTIC,
    OPENAI_COMPATIBLE,
    ;

    companion object {
        /**
         * Parses persisted provider names while safely falling back to deterministic mode.
         */
        fun fromStorage(value: String?): AiProviderType = entries.firstOrNull { it.name == value } ?: DETERMINISTIC
    }
}
