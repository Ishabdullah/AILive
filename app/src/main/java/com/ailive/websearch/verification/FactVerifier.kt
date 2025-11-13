package com.ailive.websearch.verification

import com.ailive.websearch.core.*
import com.ailive.websearch.summarizer.ResultSummarizer

/**
 * Verifies factual claims by cross-checking multiple sources.
 *
 * Process:
 * 1. Accept a claim and provider results
 * 2. Classify each result as supporting, contradicting, or neutral
 * 3. Calculate confidence based on source agreement
 * 4. Return verdict with evidence and attributions
 *
 * Safety Features:
 * - Marks unverified claims about living persons
 * - Handles conflicting evidence gracefully
 * - Provides confidence scores
 * - Includes both supporting and contradicting evidence
 *
 * @since v1.4
 */
class FactVerifier(
    private val summarizer: ResultSummarizer = ResultSummarizer()
) {

    /**
     * Verifies a factual claim against search results.
     *
     * @param claim The claim to verify
     * @param providerResults Results from search providers
     * @return FactVerificationResult with verdict, evidence, and confidence
     */
    fun verify(claim: String, providerResults: List<ProviderResult>): FactVerificationResult {
        // Extract all results
        val allResults = providerResults
            .filter { it.success }
            .flatMap { it.results }

        if (allResults.isEmpty()) {
            return createInsufficientEvidenceResult(claim)
        }

        // Classify results as supporting, contradicting, or neutral
        val evidence = classifyEvidence(claim, allResults)

        // Calculate verdict and confidence
        val (verdict, confidence) = calculateVerdict(evidence)

        // Create attributions
        val attributions = summarizer.createAttributions(providerResults, maxAttributions = 5)

        return FactVerificationResult(
            claim = claim,
            verdict = verdict,
            evidence = evidence,
            confidenceScore = confidence,
            provenance = attributions
        )
    }

    /**
     * Verifies a claim with a specific intent (e.g., person information).
     *
     * @param claim The claim to verify
     * @param providerResults Search results
     * @param intent The search intent
     * @return FactVerificationResult
     */
    fun verifyWithIntent(
        claim: String,
        providerResults: List<ProviderResult>,
        intent: SearchIntent
    ): FactVerificationResult {
        val result = verify(claim, providerResults)

        // Special handling for person-related claims
        if (intent == SearchIntent.PERSON_WHOIS && isAboutLivingPerson(claim, providerResults)) {
            // Mark as UNVERIFIED if evidence is insufficient or conflicting
            if (result.verdict == FactVerificationResult.Verdict.INCONCLUSIVE ||
                result.confidenceScore < 0.7f) {
                return result.copy(
                    verdict = FactVerificationResult.Verdict.UNVERIFIED
                )
            }
        }

        return result
    }

    /**
     * Classifies evidence into supporting, contradicting, or neutral.
     *
     * This is a simplified implementation using keyword matching.
     * In production, use semantic similarity or LLM-based classification.
     */
    private fun classifyEvidence(
        claim: String,
        results: List<SearchResultItem>
    ): FactVerificationResult.Evidence {
        val claimKeywords = extractKeywords(claim)
        val supporting = mutableListOf<SearchResultItem>()
        val contradicting = mutableListOf<SearchResultItem>()
        val neutral = mutableListOf<SearchResultItem>()

        results.forEach { result ->
            val resultKeywords = extractKeywords(result.snippet)
            val overlap = claimKeywords.intersect(resultKeywords).size
            val overlapRatio = overlap.toFloat() / claimKeywords.size

            // Detect contradiction indicators
            val hasContradiction = hasContradictionIndicators(result.snippet)

            when {
                hasContradiction -> contradicting.add(result)
                overlapRatio >= 0.6 -> supporting.add(result)
                overlapRatio >= 0.3 -> neutral.add(result)
                else -> neutral.add(result)
            }
        }

        return FactVerificationResult.Evidence(
            supporting = supporting,
            contradicting = contradicting,
            neutral = neutral
        )
    }

    /**
     * Calculates verdict and confidence based on evidence.
     */
    private fun calculateVerdict(
        evidence: FactVerificationResult.Evidence
    ): Pair<FactVerificationResult.Verdict, Float> {
        val supportCount = evidence.supporting.size
        val contradictCount = evidence.contradicting.size
        val totalCount = evidence.totalCount

        if (totalCount == 0) {
            return FactVerificationResult.Verdict.INCONCLUSIVE to 0.0f
        }

        // Calculate confidence based on agreement
        val agreementRatio = maxOf(supportCount, contradictCount).toFloat() / totalCount

        // Determine verdict
        val verdict = when {
            // Strong support
            supportCount >= 3 && supportCount > contradictCount * 2 -> {
                FactVerificationResult.Verdict.SUPPORTS
            }
            // Strong contradiction
            contradictCount >= 3 && contradictCount > supportCount * 2 -> {
                FactVerificationResult.Verdict.CONTRADICTS
            }
            // Conflicting evidence
            supportCount > 0 && contradictCount > 0 -> {
                FactVerificationResult.Verdict.INCONCLUSIVE
            }
            // Moderate support
            supportCount > contradictCount -> {
                FactVerificationResult.Verdict.SUPPORTS
            }
            // Moderate contradiction
            contradictCount > supportCount -> {
                FactVerificationResult.Verdict.CONTRADICTS
            }
            else -> {
                FactVerificationResult.Verdict.INCONCLUSIVE
            }
        }

        // Adjust confidence based on source count and agreement
        val confidence = when (verdict) {
            FactVerificationResult.Verdict.SUPPORTS,
            FactVerificationResult.Verdict.CONTRADICTS -> {
                // Higher confidence with more agreeing sources
                (agreementRatio * 0.7f + (totalCount.coerceAtMost(10) / 10.0f) * 0.3f)
                    .coerceIn(0.5f, 0.95f)
            }
            FactVerificationResult.Verdict.INCONCLUSIVE -> {
                // Low confidence for inconclusive verdicts
                0.3f + (totalCount.coerceAtMost(5) / 5.0f) * 0.2f
            }
            FactVerificationResult.Verdict.UNVERIFIED -> 0.0f
        }

        return verdict to confidence
    }

    /**
     * Extracts keywords from text (simplified).
     */
    private fun extractKeywords(text: String): Set<String> {
        // Remove common stop words
        val stopWords = setOf(
            "the", "a", "an", "and", "or", "but", "is", "are", "was", "were",
            "in", "on", "at", "to", "for", "of", "with", "by", "from", "about"
        )

        return text.lowercase()
            .split("\\s+".toRegex())
            .map { it.replace(Regex("[^a-z0-9]"), "") }
            .filter { it.length > 2 && it !in stopWords }
            .toSet()
    }

    /**
     * Detects contradiction indicators in text.
     */
    private fun hasContradictionIndicators(text: String): Boolean {
        val indicators = listOf(
            "not true", "false", "incorrect", "wrong", "debunked",
            "myth", "hoax", "misleading", "contradicts", "however",
            "but actually", "in fact", "contrary to"
        )

        val lowerText = text.lowercase()
        return indicators.any { lowerText.contains(it) }
    }

    /**
     * Detects if the claim is about a living person.
     *
     * Simplified heuristic - in production, use NER and biographical data.
     */
    private fun isAboutLivingPerson(claim: String, providerResults: List<ProviderResult>): Boolean {
        // Check if any result mentions biographical information
        val allText = providerResults
            .flatMap { it.results }
            .joinToString(" ") { it.snippet }
            .lowercase()

        val livingIndicators = listOf(
            "born", "age", "currently", "recent", "today",
            "current", "alive", "living"
        )

        val deathIndicators = listOf(
            "died", "death", "deceased", "late", "was a"
        )

        val hasLivingIndicators = livingIndicators.any { allText.contains(it) }
        val hasDeathIndicators = deathIndicators.any { allText.contains(it) }

        // If has living indicators but no death indicators, likely about living person
        return hasLivingIndicators && !hasDeathIndicators
    }

    /**
     * Creates a result for insufficient evidence.
     */
    private fun createInsufficientEvidenceResult(claim: String): FactVerificationResult {
        return FactVerificationResult(
            claim = claim,
            verdict = FactVerificationResult.Verdict.INCONCLUSIVE,
            evidence = FactVerificationResult.Evidence(),
            confidenceScore = 0.0f,
            provenance = emptyList()
        )
    }
}

/**
 * Configuration for fact verification.
 *
 * @property minSourcesRequired Minimum sources needed for high confidence
 * @property confidenceThreshold Confidence threshold for SUPPORTS/CONTRADICTS verdict
 * @property enableLivingPersonProtection Whether to apply special rules for living persons
 */
data class FactVerificationConfig(
    val minSourcesRequired: Int = 3,
    val confidenceThreshold: Float = 0.7f,
    val enableLivingPersonProtection: Boolean = true
)
