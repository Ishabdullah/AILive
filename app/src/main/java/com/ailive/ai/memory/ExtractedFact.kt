package com.ailive.ai.memory

import com.ailive.memory.database.entities.FactCategory

/**
 * ExtractedFact - Represents a fact extracted by the memory model
 *
 * This is the output of LLM-based fact extraction, containing:
 * - Categorized fact (PERSONAL_INFO, PREFERENCES, etc.)
 * - Fact text in natural language
 * - Importance score (0.0-1.0)
 *
 * @property category The category of the fact
 * @property text The fact text in natural language
 * @property importance Importance score from 0.0 (trivial) to 1.0 (critical)
 */
data class ExtractedFact(
    val category: FactCategory,
    val text: String,
    val importance: Float
) {
    init {
        require(importance in 0f..1f) { "Importance must be between 0.0 and 1.0, got $importance" }
        require(text.isNotBlank()) { "Fact text cannot be blank" }
    }

    /**
     * Convert to string representation for logging
     */
    override fun toString(): String {
        return "[${category.name}] $text (importance: ${"%.2f".format(importance)})"
    }
}
