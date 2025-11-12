# Web Search Integration - Design & Implementation Guide

**Version:** 1.4
**Date:** November 12, 2025
**Status:** Production-Ready

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Core Components](#core-components)
4. [Provider Integration](#provider-integration)
5. [Search Flow](#search-flow)
6. [Configuration](#configuration)
7. [Security & Privacy](#security--privacy)
8. [Performance Optimization](#performance-optimization)
9. [Testing Strategy](#testing-strategy)
10. [Adding New Providers](#adding-new-providers)
11. [Troubleshooting](#troubleshooting)
12. [Future Enhancements](#future-enhancements)

---

## Overview

The Web Search Integration subsystem enables AILive to access real-time information from the web while maintaining privacy, performance, and reliability. It provides:

- **Multi-provider search** - Query multiple sources simultaneously
- **Intent-based routing** - Automatically select the best providers for each query type
- **Source attribution** - Transparent citations for all information
- **Fact verification** - Cross-check claims across multiple sources
- **Smart caching** - Minimize redundant API calls
- **Rate limiting** - Respect provider quotas and avoid abuse

### Design Goals

1. **Privacy-First**: Never send device identifiers or PII to external providers
2. **Fail-Safe**: Always return best-effort results, even if some providers fail
3. **Mobile-Optimized**: Bandwidth-aware, battery-friendly, responsive UI
4. **Extensible**: Easy to add new providers or intent types
5. **Production-Ready**: Comprehensive error handling, logging, telemetry

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    PersonalityEngine                        │
│                  (Unified AI Assistant)                     │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      │ execute(params)
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                    WebSearchTool                            │
│               (AITool Implementation)                       │
│  - Parameter validation                                     │
│  - Network availability check                               │
│  - Result formatting for LLM                                │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      │ search(query)
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                  WebSearchManager                           │
│               (Orchestration Layer)                         │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ 1. Intent Detection (SearchIntentDetector)           │   │
│  │ 2. Provider Selection (based on intent + priority)   │   │
│  │ 3. Cache Check (CacheLayer)                          │   │
│  │ 4. Fan-Out Query (parallel coroutines)               │   │
│  │ 5. Result Aggregation & Ranking                      │   │
│  │ 6. Deduplication                                      │   │
│  │ 7. Summarization (ResultSummarizer)                  │   │
│  │ 8. Fact Verification (FactVerifier)                  │   │
│  │ 9. Cache Store                                        │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────┬───────────────────────────────────────┘
                      │
         ┌────────────┼────────────┬──────────────┐
         ▼            ▼            ▼              ▼
┌─────────────┐ ┌──────────┐ ┌──────────┐ ┌─────────────┐
│ Wikipedia   │ │ DuckDuck │ │OpenWeath │ │  NewsAPI    │
│ Provider    │ │GoProvider│ │erProvider│ │  Provider   │
└─────────────┘ └──────────┘ └──────────┘ └─────────────┘
         │            │            │              │
         └────────────┴────────────┴──────────────┘
                      │
                      ▼
              External APIs
     (Wikipedia, DuckDuckGo, OpenWeather, etc.)
```

### Layer Responsibilities

**Layer 1: PersonalityEngine Integration**
- Decides when to invoke web search based on user queries
- Integrates search results into conversational responses

**Layer 2: WebSearchTool (AITool)**
- Implements standard AITool interface
- Validates parameters and checks prerequisites
- Formats results for LLM consumption

**Layer 3: WebSearchManager (Orchestration)**
- Coordinates all search operations
- Manages provider lifecycle
- Implements search pipeline (intent → cache → providers → aggregation → summarization)

**Layer 4: Providers (Data Sources)**
- Abstract external APIs
- Parse provider-specific responses
- Normalize results to common format

**Layer 5: Infrastructure (Cross-Cutting)**
- HttpClientFactory: HTTP client management
- CacheLayer: Multi-tier caching
- RateLimiter: Quota management
- SearchIntentDetector: Query classification

---

## Core Components

### 1. WebSearchManager

**Responsibilities:**
- Main entry point for all search operations
- Provider registry and lifecycle management
- Search pipeline orchestration
- Telemetry and statistics

**Key Methods:**
```kotlin
suspend fun search(query: SearchQuery): SearchResponse
fun registerProvider(provider: SearchProvider)
fun getCacheStatistics(): CacheStatistics
fun getSearchStatistics(): SearchStatistics
```

**Thread Safety:** All methods are thread-safe via coroutine synchronization

### 2. SearchProvider Interface

**Contract:**
```kotlin
interface SearchProvider {
    val name: String
    val supportedIntents: Set<SearchIntent>

    suspend fun search(query: SearchQuery): ProviderResult
    suspend fun healthCheck(): ProviderStatus
    fun getPriority(intent: SearchIntent): Int
    fun canHandle(query: SearchQuery): Boolean
}
```

**Implementations:**
- `OpenWeatherProvider` - Weather queries (priority: 90)
- `WttrProvider` - Weather fallback (priority: 60)
- `WikipediaProvider` - Person/entity info (priority: 95)
- `DuckDuckGoInstantProvider` - General queries (priority: 75)
- `NewsApiProvider` - News queries (priority: 95)
- `SerpApiProvider` - General web search (priority: 85)

### 3. SearchIntentDetector

**Purpose:** Classifies user queries into intent categories

**Intents:**
- `WEATHER` - "What's the weather in Boston?"
- `PERSON_WHOIS` - "Who is Ada Lovelace?"
- `NEWS` - "Latest news about Tesla"
- `GENERAL` - "How to connect to ADB wirelessly"
- `FACT_CHECK` - "Is it true that..."
- `FORUM` - "Reddit discussion on..."
- `IMAGE` / `VIDEO` - Image/video search
- `UNKNOWN` - Ambiguous queries

**Algorithm:**
1. Pattern matching (regex for common query structures)
2. Keyword extraction and matching
3. Confidence scoring (0.0 to 1.0)
4. Fallback to GENERAL if confidence < threshold

### 4. ResultSummarizer

**Purpose:** Generate concise summaries with source attribution

**Features:**
- Brief summaries (1-3 sentences)
- Extended summaries (up to 10 sentences)
- Top-5 source citations with quotes
- Provenance tracking

**Algorithm:**
- Extractive summarization (select key sentences)
- Sentence ranking by importance
- Attribution generation from provider results

### 5. FactVerifier

**Purpose:** Verify factual claims by cross-checking multiple sources

**Process:**
1. Classify evidence (supporting, contradicting, neutral)
2. Calculate verdict based on source agreement
3. Compute confidence score
4. Generate provenance trail

**Verdicts:**
- `SUPPORTS` - Claim backed by 3+ agreeing sources
- `CONTRADICTS` - Claim refuted by 3+ sources
- `INCONCLUSIVE` - Conflicting or insufficient evidence
- `UNVERIFIED` - Special case for living persons

**Safety Features:**
- Living person protection (extra scrutiny for biographical claims)
- Requires minimum 3 sources for high-confidence verdicts
- Includes both supporting AND contradicting evidence

---

## Provider Integration

### Provider Lifecycle

1. **Registration:** `WebSearchManager.registerProvider(provider)`
2. **Health Check:** Periodic status checks via `healthCheck()`
3. **Query Execution:** Invoked by manager based on intent
4. **Result Caching:** Successful results cached automatically
5. **Rate Limiting:** Manager enforces per-provider limits

### Creating a New Provider

**Step 1: Implement SearchProvider**

```kotlin
class MyCustomProvider(
    private val apiKey: String,
    private val httpClient: OkHttpClient
) : BaseSearchProvider() {

    override val name = "MyProvider"
    override val supportedIntents = setOf(SearchIntent.GENERAL)

    override suspend fun search(query: SearchQuery): ProviderResult {
        val (result, latency) = timedSearch {
            executeSearch(query)
        }
        return result.copy(latencyMs = latency)
    }

    private suspend fun executeSearch(query: SearchQuery): ProviderResult {
        // Implementation here
    }

    override suspend fun healthCheck(): ProviderStatus {
        // Implementation here
    }

    override fun getPriority(intent: SearchIntent): Int {
        return if (intent in supportedIntents) 80 else 0
    }
}
```

**Step 2: Add to Configuration (websearch_config.yaml)**

```yaml
providers:
  - name: "MyProvider"
    enabled: true
    priority: 80
    intents:
      - GENERAL
    rate_limit:
      capacity: 100
      refill_rate: 1.0
    cache_ttl_minutes: 60
    config:
      api_key: "${MY_PROVIDER_API_KEY}"
```

**Step 3: Register with Manager**

```kotlin
val provider = MyCustomProvider(apiKey = config.apiKey)
searchManager.registerProvider(provider)
```

---

## Search Flow

### Complete Search Pipeline

```
User Query
    ↓
┌─────────────────────────────────────┐
│ 1. Intent Detection                 │
│    - Pattern matching               │
│    - Keyword extraction             │
│    - Confidence scoring             │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│ 2. Cache Check                      │
│    - Query normalization            │
│    - Cache key generation           │
│    - Return if cache hit            │
└─────────────────────────────────────┘
    ↓ (cache miss)
┌─────────────────────────────────────┐
│ 3. Provider Selection               │
│    - Filter by supported intents    │
│    - Sort by priority               │
│    - Take top N providers           │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│ 4. Parallel Provider Queries        │
│    - Check rate limits              │
│    - Execute in parallel            │
│    - Timeout handling (30s)         │
│    - Error containment              │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│ 5. Result Aggregation               │
│    - Collect all successful results │
│    - Filter errors                  │
│    - Flatten result lists           │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│ 6. Ranking & Deduplication          │
│    - Sort by confidence + recency   │
│    - Normalize URLs                 │
│    - Remove duplicates              │
│    - Take top N results             │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│ 7. Summarization                    │
│    - Generate brief summary         │
│    - Generate extended summary      │
│    - Create attribution list        │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│ 8. Fact Verification (if requested) │
│    - Classify evidence              │
│    - Calculate verdict              │
│    - Compute confidence score       │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│ 9. Cache Store                      │
│    - Cache provider results         │
│    - Cache final response           │
└─────────────────────────────────────┘
    ↓
SearchResponse (returned to user)
```

### Timing Breakdown (Typical)

| Stage | Time | Notes |
|-------|------|-------|
| Intent Detection | 1-5ms | Fast regex/keyword matching |
| Cache Check | 1-2ms | In-memory lookup |
| Provider Selection | < 1ms | Simple filtering + sorting |
| Provider Queries | 500-2000ms | Parallel execution, network-bound |
| Aggregation | 1-5ms | Flattening lists |
| Ranking & Dedup | 5-20ms | URL normalization, sorting |
| Summarization | 10-50ms | Sentence extraction |
| Fact Verification | 20-100ms | Evidence classification |
| Cache Store | 1-2ms | In-memory write |
| **Total (cache miss)** | **~1-3 seconds** | Dominated by network I/O |
| **Total (cache hit)** | **< 100ms** | Near-instant |

---

## Configuration

### Configuration File: `websearch_config.yaml`

**Location:** `app/src/main/assets/websearch_config.yaml`

**Structure:**

```yaml
global:
  max_providers_per_query: 5
  enable_summarization: true
  enable_fact_verification: true
  default_timeout_ms: 30000

providers:
  - name: "Wikipedia"
    enabled: true
    priority: 95
    intents: [PERSON_WHOIS, GENERAL]
    rate_limit:
      capacity: 100
      refill_rate: 2.0
    cache_ttl_minutes: 120

cache:
  provider_cache:
    max_size: 1000
    ttl_minutes: 60
  response_cache:
    max_size: 500
    ttl_minutes: 30

security:
  strip_device_identifiers: true
  sanitize_queries: true
  enforce_tls: true
```

### Runtime Configuration

Override config at runtime:

```kotlin
val config = WebSearchConfig(
    maxProvidersPerQuery = 3,
    enableSummarization = true,
    enableFactVerification = false
)

val manager = WebSearchManager(context, config)
```

---

## Security & Privacy

### Threat Model

**Threats:**
1. PII leakage to third-party providers
2. API key exposure in logs/crashes
3. Man-in-the-middle attacks (unencrypted HTTP)
4. Query injection attacks
5. Provider-side tracking via fingerprinting

**Mitigations:**

| Threat | Mitigation | Implementation |
|--------|-----------|----------------|
| PII Leakage | Strip device IDs, sanitize queries | Query preprocessing |
| API Key Exposure | Use Android Keystore, redact in logs | Secure storage, log filtering |
| MITM | Enforce TLS, optional cert pinning | OkHttp configuration |
| Query Injection | Input validation, parameterized requests | Provider adapters |
| Fingerprinting | Generic User-Agent, no custom headers | HttpClientFactory |

### API Key Management

**Development:**
```kotlin
val apiKeys = mapOf(
    "openweather" to "test-key-123",
    "newsapi" to "test-key-456"
)
```

**Production (Android Keystore):**
```kotlin
val keyStore = KeyStore.getInstance("AndroidKeyStore")
keyStore.load(null)

val apiKey = keyStore.getCertificate("openweather_key")
    ?.encoded?.toString(Charsets.UTF_8)
```

**Environment Variables:**
```yaml
config:
  api_key: "${OPENWEATHER_API_KEY}"
```

---

## Performance Optimization

### Caching Strategy

**Two-Tier Cache:**

1. **Provider Cache** - Raw provider results
   - Key: `"provider:query:language"`
   - TTL: 60 minutes (configurable per provider)
   - Size: 1000 entries
   - Use Case: Provider responses are expensive, cache aggressively

2. **Response Cache** - Final search responses
   - Key: `"query:language:intent"`
   - TTL: 30 minutes
   - Size: 500 entries
   - Use Case: Complete pipeline results, includes summarization

**Cache Hit Rate:**
- Expected: 40-60% for typical usage
- Monitoring: `CacheStatistics.hitRate`

### Rate Limiting

**Token Bucket Algorithm:**

```
Tokens: 100 (capacity)
Refill: 1 token/second
```

**Implementation:**
- Separate bucket per provider
- Global bucket for all providers
- Must pass both checks to execute

**Example:**
```kotlin
// Provider: 60 req/min = 1 req/s
rateLimiter.registerProvider(
    providerName = "OpenWeather",
    capacity = 60,
    refillRate = 1.0
)

// Try to acquire
if (rateLimiter.tryAcquire("OpenWeather")) {
    // Execute query
} else {
    // Rate limited, return error
}
```

### Concurrency

**Parallel Provider Queries:**
```kotlin
val results = coroutineScope {
    providers.map { provider ->
        async {
            queryProvider(provider, query)
        }
    }.awaitAll()
}
```

**Benefits:**
- 5 providers queried in parallel
- Total time = slowest provider (not sum)
- Typical: 1-2 seconds vs 5-10 seconds sequential

---

## Testing Strategy

### Unit Tests

**SearchIntentDetectorTest.kt:**
```kotlin
@Test
fun testWeatherIntentDetection() {
    val detector = SearchIntentDetector()
    val result = detector.detectIntent(
        SearchQuery(text = "What's the weather in Boston?")
    )

    assertEquals(SearchIntent.WEATHER, result.intent)
    assertTrue(result.confidence > 0.9f)
}
```

**RateLimiterTest.kt:**
```kotlin
@Test
fun testTokenBucketRefill() = runBlocking {
    val limiter = RateLimiter(capacity = 10, refillRate = 1.0)

    // Drain tokens
    repeat(10) { assertTrue(limiter.tryAcquire()) }
    assertFalse(limiter.tryAcquire())

    // Wait for refill
    delay(2000)
    assertTrue(limiter.tryAcquire())
}
```

### Integration Tests

**Provider Integration:**
```kotlin
@Test
fun testWikipediaProvider() = runBlocking {
    val mockWebServer = MockWebServer()
    mockWebServer.enqueue(
        MockResponse().setBody(wikipediaJsonFixture)
    )

    val provider = WikipediaProvider(
        httpClient = OkHttpClient.Builder()
            .baseUrl(mockWebServer.url("/"))
            .build()
    )

    val result = provider.search(
        SearchQuery(text = "Albert Einstein")
    )

    assertTrue(result.success)
    assertEquals(1, result.results.size)
    assertEquals("Albert Einstein", result.results[0].title)
}
```

---

## Adding New Providers

### Checklist

- [ ] Implement `SearchProvider` interface
- [ ] Add provider configuration to `websearch_config.yaml`
- [ ] Define supported intents and priority
- [ ] Implement `search()` method with timeout handling
- [ ] Implement `healthCheck()` for monitoring
- [ ] Handle API-specific error codes
- [ ] Parse response to `SearchResultItem` format
- [ ] Add unit tests with mocked HTTP
- [ ] Document API requirements (keys, rate limits)
- [ ] Register provider in `WebSearchTool` initialization

### Example: Reddit Provider Skeleton

```kotlin
class RedditProvider(
    private val httpClient: OkHttpClient
) : BaseSearchProvider() {

    override val name = "Reddit"
    override val supportedIntents = setOf(SearchIntent.FORUM)

    override suspend fun search(query: SearchQuery): ProviderResult {
        val subreddit = extractSubreddit(query.text) ?: "all"
        val url = "https://www.reddit.com/r/$subreddit/search.json?q=${query.text}"

        val response = httpClient.newCall(
            Request.Builder().url(url).build()
        ).execute()

        val posts = parseRedditResponse(response.body?.string())

        return createSuccessResult(
            results = posts,
            latency = 0
        )
    }

    override suspend fun healthCheck(): ProviderStatus {
        // Implementation
    }
}
```

---

## Troubleshooting

### Common Issues

**1. "No providers available for intent"**

**Cause:** No registered providers support the detected intent

**Solution:**
- Check provider registration in `WebSearchTool`
- Verify provider's `supportedIntents` set
- Check if providers are enabled in config

**2. "Rate limit exceeded"**

**Cause:** Too many requests in short time period

**Solution:**
- Increase rate limit capacity in config
- Reduce query frequency
- Check for retry loops
- Review cache configuration (increase TTL)

**3. "Provider timeout"**

**Cause:** Provider not responding within timeout

**Solution:**
- Increase `default_timeout_ms` in config
- Check network connectivity
- Verify provider API is operational
- Enable fallback providers

**4. "Cache hit rate too low"**

**Cause:** Cache not effective, too many unique queries

**Solution:**
- Increase cache size (`max_size`)
- Increase TTL (`ttl_minutes`)
- Check query normalization logic
- Review query patterns for optimization

---

## Future Enhancements

### v1.5 Roadmap

**New Providers:**
- RedditProvider for community discussions
- Image search providers (Google Images, Unsplash)
- Video search providers (YouTube API)

**Advanced Features:**
- Persistent disk cache for offline access
- Redis integration for distributed caching
- LLM-based abstractive summarization
- Semantic similarity for fact verification
- Vector embeddings for duplicate detection

**UI Enhancements:**
- Search results visualization
- Provider status dashboard
- API key management UI
- Cache management controls

**Testing:**
- Comprehensive unit test suite (80%+ coverage)
- Integration tests with recorded fixtures
- Performance benchmarks
- Stress testing for rate limiters

---

## Conclusion

The Web Search Integration subsystem provides AILive with robust, privacy-preserving access to real-time web information. By following this guide, developers can:

- Understand the architecture and data flow
- Add new search providers
- Configure caching and rate limiting
- Optimize performance
- Troubleshoot common issues
- Extend the system with new features

For questions or contributions, see the main [README.md](../README.md) or open an issue on GitHub.

---

**Document Version:** 1.0
**Last Updated:** November 12, 2025
**Maintainer:** AILive Development Team
