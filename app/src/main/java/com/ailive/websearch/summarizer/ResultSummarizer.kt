package com.ailive.websearch.summarizer

import com.ailive.websearch.core.Attribution
import com.ailive.websearch.core.ProviderResult
import com.ailive.websearch.core.SearchResultItem
import java.time.Instant

/**
 * Summarizes search results with explicit source attribution.
 *
 * Produces:
 * - Brief TL;DR summary (1-3 sentences)
 * - Extended summary with more details
 * - Top-5 source attributions with quotes
 *
 * Implementation:
 * - Currently uses extractive summarization (selecting key sentences)
 * - Future: Integrate with LLM for abstractive summarization
 *
 * @since v1.4
 */
class ResultSummarizer {

    /**
     * Creates a brief summary from search results.
     *
     * @param results List of search result items
     * @param maxSentences Maximum sentences in summary (default: 3)
     * @return Brief summary string
     */
    fun createBriefSummary(results: List<SearchResultItem>, maxSentences: Int = 3): String {
        if (results.isEmpty()) return "No results available."

        // Take the first result's snippet as the primary summary
        val primarySnippet = results.first().snippet

        // Extract sentences
        val sentences = extractSentences(primarySnippet)

        return sentences.take(maxSentences).joinToString(" ")
    }

    /**
     * Creates an extended summary from search results.
     *
     * @param results List of search result items
     * @param maxSentences Maximum sentences in extended summary (default: 10)
     * @return Extended summary string
     */
    fun createExtendedSummary(results: List<SearchResultItem>, maxSentences: Int = 10): String {
        if (results.isEmpty()) return "No detailed information available."

        val allSentences = results
            .take(5)  // Use top 5 results
            .flatMap { extractSentences(it.snippet) }
            .distinct()  // Remove duplicates

        return allSentences.take(maxSentences).joinToString(" ")
    }

    /**
     * Creates attribution list from provider results.
     *
     * @param providerResults List of provider results
     * @param maxAttributions Maximum number of attributions to return (default: 5)
     * @return List of Attribution objects with top sources
     */
    fun createAttributions(
        providerResults: List<ProviderResult>,
        maxAttributions: Int = 5
    ): List<Attribution> {
        return providerResults
            .filter { it.success && it.results.isNotEmpty() }
            .flatMap { providerResult ->
                providerResult.results.map { result ->
                    Attribution(
                        source = result.source,
                        url = result.url,
                        retrievedAt = providerResult.retrievedAt,
                        snippet = result.getQuote(maxWords = 25),
                        confidence = result.confidence
                    )
                }
            }
            .distinctBy { it.url }  // Remove duplicate URLs
            .sortedByDescending { it.confidence ?: 0.0f }  // Sort by confidence
            .take(maxAttributions)
    }

    /**
     * Creates a summary with inline citations.
     *
     * Format: "Information from multiple sources. [1] First source states... [2] Second source..."
     *
     * @param results List of search results
     * @param attributions List of attributions
     * @return Summary with inline citations
     */
    fun createCitedSummary(
        results: List<SearchResultItem>,
        attributions: List<Attribution>
    ): String {
        if (results.isEmpty() || attributions.isEmpty()) {
            return "No information available."
        }

        val summary = StringBuilder()
        summary.append("Summary from ${attributions.size} source${if (attributions.size > 1) "s" else ""}:\n\n")

        attributions.forEachIndexed { index, attribution ->
            val citation = index + 1
            summary.append("[$citation] ${attribution.source}: ${attribution.snippet}\n")
            summary.append("    (${attribution.url})\n\n")
        }

        return summary.toString().trim()
    }

    /**
     * Generates a formatted summary report.
     *
     * @param results Search results
     * @param providerResults Provider results for attribution
     * @return Formatted summary report
     */
    fun generateSummaryReport(
        results: List<SearchResultItem>,
        providerResults: List<ProviderResult>
    ): SummaryReport {
        val brief = createBriefSummary(results, maxSentences = 3)
        val extended = createExtendedSummary(results, maxSentences = 10)
        val attributions = createAttributions(providerResults, maxAttributions = 5)

        return SummaryReport(
            briefSummary = brief,
            extendedSummary = extended,
            attributions = attributions,
            totalSources = providerResults.count { it.success },
            generatedAt = Instant.now()
        )
    }

    /**
     * Extracts sentences from text using simple heuristics.
     */
    private fun extractSentences(text: String): List<String> {
        return text
            .split(Regex("[.!?]+"))
            .map { it.trim() }
            .filter { it.length > 20 }  // Filter out very short fragments
            .map { if (!it.endsWith(".")) "$it." else it }
    }

    /**
     * Ranks sentences by importance (simplified scoring).
     */
    private fun rankSentences(sentences: List<String>): List<String> {
        return sentences.sortedByDescending { sentence ->
            // Simple scoring: longer sentences with more keywords rank higher
            val lengthScore = sentence.length.coerceAtMost(200) / 200.0
            val wordCount = sentence.split(" ").size
            val wordScore = wordCount.coerceAtMost(30) / 30.0

            lengthScore * 0.3 + wordScore * 0.7
        }
    }
}

/**
 * Summary report with all summary types and metadata.
 *
 * @property briefSummary Brief 1-3 sentence summary
 * @property extendedSummary Extended summary with more details
 * @property attributions Source attributions with quotes
 * @property totalSources Number of sources consulted
 * @property generatedAt When this summary was generated
 */
data class SummaryReport(
    val briefSummary: String,
    val extendedSummary: String,
    val attributions: List<Attribution>,
    val totalSources: Int,
    val generatedAt: Instant
) {
    /**
     * Returns a formatted text representation of the summary.
     */
    fun toFormattedText(): String {
        return buildString {
            appendLine("SUMMARY")
            appendLine("=".repeat(60))
            appendLine()
            appendLine("Brief: $briefSummary")
            appendLine()
            appendLine("Extended:")
            appendLine(extendedSummary)
            appendLine()
            appendLine("SOURCES (${attributions.size} of $totalSources)")
            appendLine("=".repeat(60))
            attributions.forEachIndexed { index, attr ->
                appendLine("[${index + 1}] ${attr.source}")
                appendLine("    ${attr.snippet}")
                appendLine("    ${attr.url}")
                appendLine()
            }
            appendLine("Generated at: $generatedAt")
        }
    }
}
