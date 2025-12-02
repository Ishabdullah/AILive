package com.adaptheon.core.knowledge

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap
import java.time.LocalDateTime

/**
 * Knowledge management system with graph capabilities
 * Provides structured knowledge representation and relationship tracking
 */
class KnowledgeCore {
    private val knowledgeGraph = KnowledgeGraph()
    private val factExtractor = FactExtractor()
    private val relationshipAnalyzer = RelationshipAnalyzer()
    
    /**
     * Add knowledge item to the graph
     */
    suspend fun addKnowledge(
        content: String,
        type: KnowledgeType,
        confidence: Double = 1.0,
        source: String? = null,
        metadata: Map<String, Any> = emptyMap()
    ): KnowledgeNode {
        val facts = factExtractor.extractFacts(content)
        val nodeId = generateNodeId()
        
        val node = KnowledgeNode(
            id = nodeId,
            content = content,
            type = type,
            confidence = confidence,
            facts = facts,
            source = source,
            metadata = metadata,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        knowledgeGraph.addNode(node)
        
        // Auto-detect and create relationships
        val existingNodes = knowledgeGraph.getAllNodes()
        existingNodes.forEach { existingNode ->
            if (existingNode.id != nodeId) {
                val relationships = relationshipAnalyzer.analyzeRelationship(node, existingNode)
                relationships.forEach { relationship ->
                    knowledgeGraph.addRelationship(relationship)
                }
            }
        }
        
        return node
    }
    
    /**
     * Query knowledge graph
     */
    suspend fun query(query: KnowledgeQuery): Flow<KnowledgeResult> = flow {
        val results = when (query.type) {
            QueryType.CONTENT_SEARCH -> searchByContent(query)
            QueryType.FACT_SEARCH -> searchByFacts(query)
            QueryType.RELATIONSHIP_SEARCH -> searchByRelationships(query)
            QueryType.TYPE_SEARCH -> searchByType(query)
            QueryType.SEMANTIC_SEARCH -> searchSemantic(query)
        }
        
        results.forEach { result ->
            emit(result)
        }
    }
    
    /**
     * Get knowledge node by ID
     */
    suspend fun getNode(nodeId: String): KnowledgeNode? {
        return knowledgeGraph.getNode(nodeId)
    }
    
    /**
     * Get related knowledge nodes
     */
    suspend fun getRelatedNodes(
        nodeId: String,
        relationshipType: RelationshipType? = null,
        maxDepth: Int = 2
    ): List<KnowledgeNode> {
        return knowledgeGraph.getRelatedNodes(nodeId, relationshipType, maxDepth)
    }
    
    /**
     * Get relationships for a node
     */
    suspend fun getRelationships(nodeId: String): List<Relationship> {
        return knowledgeGraph.getNodeRelationships(nodeId)
    }
    
    /**
     * Update knowledge node
     */
    suspend fun updateNode(
        nodeId: String,
        content: String? = null,
        confidence: Double? = null,
        metadata: Map<String, Any>? = null
    ): Boolean {
        val existingNode = knowledgeGraph.getNode(nodeId) ?: return false
        
        val updatedNode = existingNode.copy(
            content = content ?: existingNode.content,
            confidence = confidence ?: existingNode.confidence,
            metadata = metadata ?: existingNode.metadata,
            updatedAt = LocalDateTime.now()
        )
        
        // Re-extract facts if content changed
        val finalNode = if (content != null) {
            val newFacts = factExtractor.extractFacts(content)
            updatedNode.copy(facts = newFacts)
        } else {
            updatedNode
        }
        
        return knowledgeGraph.updateNode(nodeId, finalNode)
    }
    
    /**
     * Delete knowledge node and its relationships
     */
    suspend fun deleteNode(nodeId: String): Boolean {
        return knowledgeGraph.deleteNode(nodeId)
    }
    
    /**
     * Add explicit relationship between nodes
     */
    suspend fun addRelationship(
        fromNodeId: String,
        toNodeId: String,
        type: RelationshipType,
        confidence: Double = 1.0,
        metadata: Map<String, Any> = emptyMap()
    ): Boolean {
        val relationship = Relationship(
            id = generateRelationshipId(),
            fromNodeId = fromNodeId,
            toNodeId = toNodeId,
            type = type,
            confidence = confidence,
            metadata = metadata,
            createdAt = LocalDateTime.now()
        )
        
        return knowledgeGraph.addRelationship(relationship)
    }
    
    /**
     * Remove relationship
     */
    suspend fun removeRelationship(relationshipId: String): Boolean {
        return knowledgeGraph.removeRelationship(relationshipId)
    }
    
    /**
     * Get knowledge graph statistics
     */
    suspend fun getStats(): KnowledgeGraphStats {
        return knowledgeGraph.getStats()
    }
    
    /**
     * Export knowledge graph
     */
    suspend fun exportGraph(): GraphExport {
        return knowledgeGraph.export()
    }
    
    /**
     * Import knowledge graph
     */
    suspend fun importGraph(graphExport: GraphExport): ImportResult {
        return knowledgeGraph.import(graphExport)
    }
    
    /**
     * Find shortest path between nodes
     */
    suspend fun findShortestPath(
        fromNodeId: String,
        toNodeId: String,
        maxDepth: Int = 10
    ): List<String>? {
        return knowledgeGraph.findShortestPath(fromNodeId, toNodeId, maxDepth)
    }
    
    /**
     * Get knowledge by type
     */
    suspend fun getKnowledgeByType(type: KnowledgeType): List<KnowledgeNode> {
        return knowledgeGraph.getNodesByType(type)
    }
    
    /**
     * Get knowledge by confidence threshold
     */
    suspend fun getKnowledgeByConfidence(minConfidence: Double): List<KnowledgeNode> {
        return knowledgeGraph.getNodesByConfidence(minConfidence)
    }
    
    private fun searchByContent(query: KnowledgeQuery): List<KnowledgeResult> {
        val nodes = knowledgeGraph.getAllNodes()
        return nodes.filter { node ->
            node.content.contains(query.query, ignoreCase = true) ||
            node.facts.any { fact -> fact.content.contains(query.query, ignoreCase = true) }
        }.map { node ->
            KnowledgeResult(node, 1.0, MatchType.CONTENT)
        }
    }
    
    private fun searchByFacts(query: KnowledgeQuery): List<KnowledgeResult> {
        val nodes = knowledgeGraph.getAllNodes()
        return nodes.filter { node ->
            node.facts.any { fact ->
                fact.content.contains(query.query, ignoreCase = true) ||
                fact.type.toString().contains(query.query, ignoreCase = true)
            }
        }.map { node ->
            val relevanceScore = calculateFactRelevance(node, query.query)
            KnowledgeResult(node, relevanceScore, MatchType.FACT)
        }
    }
    
    private fun searchByRelationships(query: KnowledgeQuery): List<KnowledgeResult> {
        val allNodes = mutableListOf<KnowledgeResult>()
        knowledgeGraph.getAllNodes().forEach { node ->
            val relationships = knowledgeGraph.getNodeRelationships(node.id)
            relationships.forEach { relationship ->
                if (relationship.type.toString().contains(query.query, ignoreCase = true)) {
                    allNodes.add(KnowledgeResult(node, 0.8, MatchType.RELATIONSHIP))
                }
            }
        }
        return allNodes
    }
    
    private fun searchByType(query: KnowledgeQuery): List<KnowledgeResult> {
        try {
            val type = KnowledgeType.valueOf(query.query.uppercase())
            val nodes = knowledgeGraph.getNodesByType(type)
            return nodes.map { node ->
                KnowledgeResult(node, 1.0, MatchType.TYPE)
            }
        } catch (e: IllegalArgumentException) {
            return emptyList()
        }
    }
    
    private fun searchSemantic(query: KnowledgeQuery): List<KnowledgeResult> {
        // Placeholder for semantic search - would integrate with VectorCore in real implementation
        val nodes = knowledgeGraph.getAllNodes()
        return nodes.mapNotNull { node ->
            val similarity = calculateSemanticSimilarity(query.query, node.content)
            if (similarity > 0.5) {
                KnowledgeResult(node, similarity, MatchType.SEMANTIC)
            } else null
        }.sortedByDescending { it.relevanceScore }
    }
    
    private fun calculateFactRelevance(node: KnowledgeNode, query: String): Double {
        val matchingFacts = node.facts.count { fact ->
            fact.content.contains(query, ignoreCase = true)
        }
        return matchingFacts.toDouble() / node.facts.size.toDouble()
    }
    
    private fun calculateSemanticSimilarity(query: String, content: String): Double {
        // Simple word overlap for placeholder
        val queryWords = query.lowercase().split("\\s+".toRegex()).toSet()
        val contentWords = content.lowercase().split("\\s+".toRegex()).toSet()
        
        val intersection = queryWords.intersect(contentWords)
        val union = queryWords.union(contentWords)
        
        return if (union.isNotEmpty()) {
            intersection.size.toDouble() / union.size.toDouble()
        } else 0.0
    }
    
    private fun generateNodeId(): String {
        return "node_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
    
    private fun generateRelationshipId(): String {
        return "rel_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
    
    data class KnowledgeNode(
        val id: String,
        val content: String,
        val type: KnowledgeType,
        val confidence: Double,
        val facts: List<ExtractedFact>,
        val source: String?,
        val metadata: Map<String, Any>,
        val createdAt: LocalDateTime,
        val updatedAt: LocalDateTime
    )
    
    data class ExtractedFact(
        val content: String,
        val type: FactType,
        val confidence: Double,
        val entities: List<String> = emptyList()
    )
    
    data class Relationship(
        val id: String,
        val fromNodeId: String,
        val toNodeId: String,
        val type: RelationshipType,
        val confidence: Double,
        val metadata: Map<String, Any>,
        val createdAt: LocalDateTime
    )
    
    data class KnowledgeQuery(
        val query: String,
        val type: QueryType = QueryType.CONTENT_SEARCH,
        val filters: Map<String, Any> = emptyMap(),
        val limit: Int = 50
    )
    
    data class KnowledgeResult(
        val node: KnowledgeNode,
        val relevanceScore: Double,
        val matchType: MatchType
    )
    
    data class KnowledgeGraphStats(
        val totalNodes: Int,
        val totalRelationships: Int,
        val typeDistribution: Map<KnowledgeType, Int>,
        val relationshipDistribution: Map<RelationshipType, Int>,
        val averageConfidence: Double
    )
    
    data class GraphExport(
        val nodes: List<KnowledgeNode>,
        val relationships: List<Relationship>,
        val exportDate: LocalDateTime
    )
    
    data class ImportResult(
        val nodesImported: Int,
        val relationshipsImported: Int,
        val errors: List<String>
    )
    
    enum class KnowledgeType {
        FACT, CONCEPT, ENTITY, EVENT, RELATIONSHIP, SKILL, PREFERENCE, RULE
    }
    
    enum class FactType {
        ENTITY, PROPERTY, RELATION, QUANTITY, TEMPORAL, CAUSAL
    }
    
    enum class RelationshipType {
        IS_A, PART_OF, RELATED_TO, CAUSES, CONTAINS, INSTANCE_OF, SIMILAR_TO, OPPOSITE_OF
    }
    
    enum class QueryType {
        CONTENT_SEARCH, FACT_SEARCH, RELATIONSHIP_SEARCH, TYPE_SEARCH, SEMANTIC_SEARCH
    }
    
    enum class MatchType {
        CONTENT, FACT, RELATIONSHIP, TYPE, SEMANTIC
    }
}

// Placeholder implementations
private class KnowledgeGraph {
    private val nodes = ConcurrentHashMap<String, KnowledgeCore.KnowledgeNode>()
    private val relationships = ConcurrentHashMap<String, KnowledgeCore.Relationship>()
    private val nodeRelationships = ConcurrentHashMap<String, MutableList<String>>()
    
    suspend fun addNode(node: KnowledgeCore.KnowledgeNode) {
        nodes[node.id] = node
    }
    
    suspend fun updateNode(nodeId: String, node: KnowledgeCore.KnowledgeNode): Boolean {
        return if (nodes.containsKey(nodeId)) {
            nodes[nodeId] = node
            true
        } else false
    }
    
    suspend fun deleteNode(nodeId: String): Boolean {
        // Remove relationships first
        val relationshipIds = nodeRelationships[nodeId] ?: emptyList()
        relationshipIds.forEach { relId ->
            relationships.remove(relId)
        }
        nodeRelationships.remove(nodeId)
        
        return nodes.remove(nodeId) != null
    }
    
    suspend fun addRelationship(relationship: KnowledgeCore.Relationship): Boolean {
        if (!nodes.containsKey(relationship.fromNodeId) || !nodes.containsKey(relationship.toNodeId)) {
            return false
        }
        
        relationships[relationship.id] = relationship
        
        nodeRelationships.getOrPut(relationship.fromNodeId) { mutableListOf() }.add(relationship.id)
        nodeRelationships.getOrPut(relationship.toNodeId) { mutableListOf() }.add(relationship.id)
        
        return true
    }
    
    suspend fun removeRelationship(relationshipId: String): Boolean {
        val relationship = relationships[relationshipId] ?: return false
        
        relationships.remove(relationshipId)
        
        nodeRelationships[relationship.fromNodeId]?.remove(relationshipId)
        nodeRelationships[relationship.toNodeId]?.remove(relationshipId)
        
        return true
    }
    
    suspend fun getNode(nodeId: String): KnowledgeCore.KnowledgeNode? {
        return nodes[nodeId]
    }
    
    suspend fun getAllNodes(): List<KnowledgeCore.KnowledgeNode> {
        return nodes.values.toList()
    }
    
    suspend fun getRelatedNodes(nodeId: String, relationshipType: KnowledgeCore.RelationshipType?, maxDepth: Int): List<KnowledgeCore.KnowledgeNode> {
        val visited = mutableSetOf<String>()
        val queue = mutableListOf<Pair<String, Int>>()
        val result = mutableListOf<KnowledgeCore.KnowledgeNode>()
        
        queue.add(Pair(nodeId, 0))
        visited.add(nodeId)
        
        while (queue.isNotEmpty()) {
            val (currentId, depth) = queue.removeFirst()
            
            if (depth >= maxDepth) continue
            
            val relIds = nodeRelationships[currentId] ?: continue
            
            relIds.forEach { relId ->
                val relationship = relationships[relId] ?: return@forEach
                
                if (relationshipType == null || relationship.type == relationshipType) {
                    val otherNodeId = if (relationship.fromNodeId == currentId) {
                        relationship.toNodeId
                    } else {
                        relationship.fromNodeId
                    }
                    
                    if (otherNodeId !in visited) {
                        visited.add(otherNodeId)
                        queue.add(Pair(otherNodeId, depth + 1))
                        nodes[otherNodeId]?.let { result.add(it) }
                    }
                }
            }
        }
        
        return result
    }
    
    suspend fun getNodeRelationships(nodeId: String): List<KnowledgeCore.Relationship> {
        val relIds = nodeRelationships[nodeId] ?: return emptyList()
        return relIds.mapNotNull { relationships[it] }
    }
    
    suspend fun getNodesByType(type: KnowledgeCore.KnowledgeType): List<KnowledgeCore.KnowledgeNode> {
        return nodes.values.filter { it.type == type }
    }
    
    suspend fun getNodesByConfidence(minConfidence: Double): List<KnowledgeCore.KnowledgeNode> {
        return nodes.values.filter { it.confidence >= minConfidence }
    }
    
    suspend fun getStats(): KnowledgeCore.KnowledgeGraphStats {
        val nodeList = nodes.values.toList()
        val relationshipList = relationships.values.toList()
        
        return KnowledgeCore.KnowledgeGraphStats(
            totalNodes = nodeList.size,
            totalRelationships = relationshipList.size,
            typeDistribution = nodeList.groupBy { it.type }.mapValues { it.value.size },
            relationshipDistribution = relationshipList.groupBy { it.type }.mapValues { it.value.size },
            averageConfidence = if (nodeList.isNotEmpty()) nodeList.map { it.confidence }.average() else 0.0
        )
    }
    
    suspend fun export(): KnowledgeCore.GraphExport {
        return KnowledgeCore.GraphExport(
            nodes = getAllNodes(),
            relationships = relationships.values.toList(),
            exportDate = LocalDateTime.now()
        )
    }
    
    suspend fun import(graphExport: KnowledgeCore.GraphExport): KnowledgeCore.ImportResult {
        var nodesImported = 0
        var relationshipsImported = 0
        val errors = mutableListOf<String>()
        
        try {
            graphExport.nodes.forEach { node ->
                addNode(node)
                nodesImported++
            }
            
            graphExport.relationships.forEach { relationship ->
                if (addRelationship(relationship)) {
                    relationshipsImported++
                } else {
                    errors.add("Failed to import relationship: ${relationship.id}")
                }
            }
        } catch (e: Exception) {
            errors.add("Import error: ${e.message}")
        }
        
        return KnowledgeCore.ImportResult(nodesImported, relationshipsImported, errors)
    }
    
    suspend fun findShortestPath(fromNodeId: String, toNodeId: String, maxDepth: Int): List<String>? {
        val visited = mutableSetOf<String>()
        val queue = mutableListOf<Pair<String, List<String>>>()
        
        queue.add(Pair(fromNodeId, listOf(fromNodeId)))
        visited.add(fromNodeId)
        
        while (queue.isNotEmpty()) {
            val (currentId, path) = queue.removeFirst()
            
            if (currentId == toNodeId) {
                return path
            }
            
            if (path.size >= maxDepth) continue
            
            val relatedNodes = getRelatedNodes(currentId, null, 1)
            relatedNodes.forEach { node ->
                if (node.id !in visited) {
                    visited.add(node.id)
                    queue.add(Pair(node.id, path + node.id))
                }
            }
        }
        
        return null
    }
}

private class FactExtractor {
    suspend fun extractFacts(content: String): List<KnowledgeCore.ExtractedFact> {
        val facts = mutableListOf<KnowledgeCore.ExtractedFact>()
        
        // Simple fact extraction logic - in real implementation would use NLP
        val sentences = content.split(".").filter { it.isNotBlank() }
        
        sentences.forEach { sentence ->
            when {
                sentence.contains("is a") || sentence.contains("is an") -> {
                    facts.add(KnowledgeCore.ExtractedFact(
                        content = sentence.trim(),
                        type = KnowledgeCore.FactType.ENTITY,
                        confidence = 0.8
                    ))
                }
                sentence.contains("has") || sentence.contains("contains") -> {
                    facts.add(KnowledgeCore.ExtractedFact(
                        content = sentence.trim(),
                        type = KnowledgeCore.FactType.PROPERTY,
                        confidence = 0.7
                    ))
                }
                sentence.contains("because") || sentence.contains("causes") -> {
                    facts.add(KnowledgeCore.ExtractedFact(
                        content = sentence.trim(),
                        type = KnowledgeCore.FactType.CAUSAL,
                        confidence = 0.6
                    ))
                }
                else -> {
                    facts.add(KnowledgeCore.ExtractedFact(
                        content = sentence.trim(),
                        type = KnowledgeCore.FactType.RELATION,
                        confidence = 0.5
                    ))
                }
            }
        }
        
        return facts
    }
}

private class RelationshipAnalyzer {
    suspend fun analyzeRelationship(node1: KnowledgeCore.KnowledgeNode, node2: KnowledgeCore.KnowledgeNode): List<KnowledgeCore.Relationship> {
        val relationships = mutableListOf<KnowledgeCore.Relationship>()
        
        // Simple relationship analysis based on content similarity
        val similarity = calculateContentSimilarity(node1.content, node2.content)
        
        if (similarity > 0.7) {
            relationships.add(KnowledgeCore.Relationship(
                id = "",
                fromNodeId = node1.id,
                toNodeId = node2.id,
                type = KnowledgeCore.RelationshipType.SIMILAR_TO,
                confidence = similarity,
                metadata = emptyMap(),
                createdAt = LocalDateTime.now()
            ))
        }
        
        // Type-based relationships
        if (node1.type == node2.type && node1.type == KnowledgeCore.KnowledgeType.ENTITY) {
            relationships.add(KnowledgeCore.Relationship(
                id = "",
                fromNodeId = node1.id,
                toNodeId = node2.id,
                type = KnowledgeCore.RelationshipType.RELATED_TO,
                confidence = 0.6,
                metadata = emptyMap(),
                createdAt = LocalDateTime.now()
            ))
        }
        
        return relationships
    }
    
    private fun calculateContentSimilarity(content1: String, content2: String): Double {
        val words1 = content1.lowercase().split("\\s+".toRegex()).toSet()
        val words2 = content2.lowercase().split("\\s+".toRegex()).toSet()
        
        val intersection = words1.intersect(words2)
        val union = words1.union(words2)
        
        return if (union.isNotEmpty()) intersection.size.toDouble() / union.size.toDouble() else 0.0
    }
}