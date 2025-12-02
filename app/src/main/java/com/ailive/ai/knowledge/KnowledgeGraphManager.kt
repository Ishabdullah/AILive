package com.ailive.ai.knowledge

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Knowledge Graph Manager
 * Expands and manages the knowledge graph for enhanced AI understanding and reasoning
 */
class KnowledgeGraphManager(private val context: Context) {
    
    companion object {
        private const val TAG = "KnowledgeGraphManager"
        private const val KNOWLEDGE_GRAPH_FILE = "knowledge_graph.json"
        private const val ENTITIES_FILE = "entities.json"
        private const val RELATIONS_FILE = "relations.json"
    }
    
    // Graph data structures
    private val entities = ConcurrentHashMap<String, KnowledgeEntity>()
    private val relations = ConcurrentHashMap<String, List<KnowledgeRelation>>()
    private val graph = KnowledgeGraph()
    
    // State management
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    private val _graphSize = MutableStateFlow(0)
    val graphSize: StateFlow<Int> = _graphSize.asStateFlow()
    
    private val _lastUpdateTime = MutableStateFlow(0L)
    val lastUpdateTime: StateFlow<Long> = _lastUpdateTime.asStateFlow()
    
    // Graph statistics
    private val _statistics = MutableStateFlow(KnowledgeGraphStatistics())
    val statistics: StateFlow<KnowledgeGraphStatistics> = _statistics.asStateFlow()
    
    // File paths
    private val knowledgeGraphDir = File(context.filesDir, "knowledge_graph")
    private val knowledgeGraphFile = File(knowledgeGraphDir, KNOWLEDGE_GRAPH_FILE)
    private val entitiesFile = File(knowledgeGraphDir, ENTITIES_FILE)
    private val relationsFile = File(knowledgeGraphDir, RELATIONS_FILE)
    
    /**
     * Initialize the knowledge graph manager
     */
    suspend fun initialize(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Create directories if they don't exist
                if (!knowledgeGraphDir.exists()) {
                    knowledgeGraphDir.mkdirs()
                }
                
                // Load existing knowledge graph or create new one
                if (knowledgeGraphFile.exists()) {
                    loadKnowledgeGraph()
                } else {
                    createInitialKnowledgeGraph()
                }
                
                _isInitialized.value = true
                updateStatistics()
                true
                
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Add a new entity to the knowledge graph
     */
    suspend fun addEntity(
        id: String,
        type: EntityType,
        name: String,
        properties: Map<String, Any> = emptyMap(),
        confidence: Float = 1.0f
    ): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                val entity = KnowledgeEntity(
                    id = id,
                    type = type,
                    name = name,
                    properties = properties,
                    confidence = confidence,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                
                entities[id] = entity
                graph.addNode(entity)
                
                _lastUpdateTime.value = System.currentTimeMillis()
                _graphSize.value = entities.size
                
                true
                
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Add a relationship between two entities
     */
    suspend fun addRelation(
        fromEntityId: String,
        toEntityId: String,
        relationType: RelationType,
        confidence: Float = 1.0f,
        properties: Map<String, Any> = emptyMap()
    ): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                val fromEntity = entities[fromEntityId]
                val toEntity = entities[toEntityId]
                
                if (fromEntity == null || toEntity == null) {
                    return@withContext false
                }
                
                val relation = KnowledgeRelation(
                    id = generateRelationId(fromEntityId, toEntityId, relationType),
                    fromEntityId = fromEntityId,
                    toEntityId = toEntityId,
                    type = relationType,
                    confidence = confidence,
                    properties = properties,
                    createdAt = System.currentTimeMillis()
                )
                
                // Update relations mapping
                val fromRelations = relations[fromEntityId]?.toMutableList() ?: mutableListOf()
                fromRelations.add(relation)
                relations[fromEntityId] = fromRelations
                
                // Add edge to graph
                graph.addEdge(fromEntity, toEntity, relation)
                
                _lastUpdateTime.value = System.currentTimeMillis()
                true
                
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Find entities by type or properties
     */
    suspend fun findEntities(
        type: EntityType? = null,
        properties: Map<String, Any> = emptyMap(),
        limit: Int = 100
    ): List<KnowledgeEntity> {
        return withContext(Dispatchers.Default) {
            var results = entities.values.toList()
            
            // Filter by type
            type?.let { entityType ->
                results = results.filter { it.type == entityType }
            }
            
            // Filter by properties
            if (properties.isNotEmpty()) {
                results = results.filter { entity ->
                    properties.all { (key, value) ->
                        entity.properties[key] == value
                    }
                }
            }
            
            // Sort by confidence and limit
            results.sortedByDescending { it.confidence }.take(limit)
        }
    }
    
    /**
     * Find relationships for an entity
     */
    suspend fun findRelations(
        entityId: String,
        relationType: RelationType? = null
    ): List<KnowledgeRelation> {
        return withContext(Dispatchers.Default) {
            val entityRelations = relations[entityId] ?: emptyList()
            
            relationType?.let { type ->
                entityRelations.filter { it.type == type }
            } ?: entityRelations
        }
    }
    
    /**
     * Find path between two entities in the knowledge graph
     */
    suspend fun findPath(
        fromEntityId: String,
        toEntityId: String,
        maxDepth: Int = 5
    ): List<KnowledgePath>? {
        return withContext(Dispatchers.Default) {
            try {
                val fromEntity = entities[fromEntityId]
                val toEntity = entities[toEntityId]
                
                if (fromEntity == null || toEntity == null) {
                    return@withContext null
                }
                
                graph.findPath(fromEntity, toEntity, maxDepth)
                
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Expand knowledge graph from text input
     */
    suspend fun expandFromText(
        text: String,
        context: Map<String, Any> = emptyMap()
    ): KnowledgeExpansionResult {
        return withContext(Dispatchers.Default) {
            try {
                // Extract entities and relations from text
                val extractionResult = extractKnowledgeFromText(text)
                
                var entitiesAdded = 0
                var relationsAdded = 0
                
                // Add extracted entities
                extractionResult.entities.forEach { entityData ->
                    if (addEntity(
                            id = entityData.id,
                            type = entityData.type,
                            name = entityData.name,
                            properties = entityData.properties,
                            confidence = entityData.confidence
                        )) {
                        entitiesAdded++
                    }
                }
                
                // Add extracted relations
                extractionResult.relations.forEach { relationData ->
                    if (addRelation(
                            fromEntityId = relationData.fromEntityId,
                            toEntityId = relationData.toEntityId,
                            relationType = relationData.type,
                            confidence = relationData.confidence,
                            properties = relationData.properties
                        )) {
                        relationsAdded++
                    }
                }
                
                _lastUpdateTime.value = System.currentTimeMillis()
                updateStatistics()
                
                KnowledgeExpansionResult(
                    success = true,
                    entitiesAdded = entitiesAdded,
                    relationsAdded = relationsAdded,
                    processingTime = extractionResult.processingTime
                )
                
            } catch (e: Exception) {
                KnowledgeExpansionResult(
                    success = false,
                    error = e.message
                )
            }
        }
    }
    
    /**
     * Get related entities for a given entity
     */
    suspend fun getRelatedEntities(
        entityId: String,
        maxDepth: Int = 2,
        limit: Int = 50
    ): List<RelatedEntity> {
        return withContext(Dispatchers.Default) {
            try {
                val entity = entities[entityId] ?: return@withContext emptyList()
                val relatedEntities = mutableListOf<RelatedEntity>()
                val visited = mutableSetOf<String>()
                val queue = mutableListOf<Pair<String, Int>>()
                
                queue.add(entityId to 0)
                visited.add(entityId)
                
                while (queue.isNotEmpty() && relatedEntities.size < limit) {
                    val (currentId, depth) = queue.removeAt(0)
                    
                    if (depth >= maxDepth) continue
                    
                    val relations = relations[currentId] ?: emptyList()
                    
                    relations.forEach { relation ->
                        val targetId = relation.toEntityId
                        if (targetId !in visited && depth < maxDepth) {
                            visited.add(targetId)
                            queue.add(targetId to depth + 1)
                            
                            entities[targetId]?.let { targetEntity ->
                                relatedEntities.add(
                                    RelatedEntity(
                                        entity = targetEntity,
                                        relation = relation,
                                        distance = depth + 1,
                                        confidence = relation.confidence
                                    )
                                )
                            }
                        }
                    }
                }
                
                relatedEntities.sortedBy { it.distance }.take(limit)
                
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * Save knowledge graph to disk
     */
    suspend fun saveKnowledgeGraph(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Save entities
                val entitiesJson = JSONArray()
                entities.values.forEach { entity ->
                    entitiesJson.put(entity.toJson())
                }
                entitiesFile.writeText(entitiesJson.toString())
                
                // Save relations
                val relationsJson = JSONArray()
                relations.values.flatten().forEach { relation ->
                    relationsJson.put(relation.toJson())
                }
                relationsFile.writeText(relationsJson.toString())
                
                // Save graph metadata
                val graphMetadata = JSONObject().apply {
                    put("version", "1.0")
                    put("entityCount", entities.size)
                    put("relationCount", relations.values.sumOf { it.size })
                    put("lastUpdateTime", _lastUpdateTime.value)
                }
                knowledgeGraphFile.writeText(graphMetadata.toString())
                
                true
                
            } catch (e: Exception) {
                false
            }
        }
    }
    
    // Private helper methods
    
    private fun loadKnowledgeGraph() {
        try {
            // Load entities
            if (entitiesFile.exists()) {
                val entitiesJson = JSONArray(entitiesFile.readText())
                for (i in 0 until entitiesJson.length()) {
                    val entityJson = entitiesJson.getJSONObject(i)
                    val entity = KnowledgeEntity.fromJson(entityJson)
                    entities[entity.id] = entity
                    graph.addNode(entity)
                }
            }
            
            // Load relations
            if (relationsFile.exists()) {
                val relationsJson = JSONArray(relationsFile.readText())
                for (i in 0 until relationsJson.length()) {
                    val relationJson = relationsJson.getJSONObject(i)
                    val relation = KnowledgeRelation.fromJson(relationJson)
                    
                    val fromRelations = relations[relation.fromEntityId]?.toMutableList() ?: mutableListOf()
                    fromRelations.add(relation)
                    relations[relation.fromEntityId] = fromRelations
                    
                    val fromEntity = entities[relation.fromEntityId]
                    val toEntity = entities[relation.toEntityId]
                    if (fromEntity != null && toEntity != null) {
                        graph.addEdge(fromEntity, toEntity, relation)
                    }
                }
            }
            
            _graphSize.value = entities.size
            
        } catch (e: Exception) {
            // If loading fails, start with empty graph
            createInitialKnowledgeGraph()
        }
    }
    
    private fun createInitialKnowledgeGraph() {
        // Initialize with some basic entities if needed
        // This can be extended to include domain-specific initial knowledge
    }
    
    private fun extractKnowledgeFromText(text: String): TextExtractionResult {
        // Implementation for text-based knowledge extraction
        // This would use NLP techniques to extract entities and relations
        return TextExtractionResult(
            entities = emptyList(),
            relations = emptyList(),
            processingTime = 100L
        )
    }
    
    private fun generateRelationId(fromEntityId: String, toEntityId: String, relationType: RelationType): String {
        return "${fromEntityId}_${relationType.name}_${toEntityId}"
    }
    
    private fun updateStatistics() {
        val totalRelations = relations.values.sumOf { it.size }
        _statistics.value = KnowledgeGraphStatistics(
            entityCount = entities.size,
            relationCount = totalRelations,
            entityTypes = entities.values.groupBy { it.type }.mapValues { it.value.size },
            relationTypes = relations.values.flatten().groupBy { it.type }.mapValues { it.value.size },
            lastUpdateTime = _lastUpdateTime.value
        )
    }
}

// Data classes and enums

enum class EntityType {
    PERSON,
    ORGANIZATION,
    LOCATION,
    CONCEPT,
    EVENT,
    OBJECT,
    TECHNOLOGY,
    CUSTOM
}

enum class RelationType {
    IS_A,
    PART_OF,
    RELATED_TO,
    LOCATED_AT,
    CREATED_BY,
    OWNS,
    WORKS_FOR,
    KNOWS,
    SIMILAR_TO,
    CAUSES,
    ENABLES,
    REQUIRES,
    CUSTOM
}

data class KnowledgeEntity(
    val id: String,
    val type: EntityType,
    val name: String,
    val properties: Map<String, Any>,
    val confidence: Float,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("type", type.name)
            put("name", name)
            put("properties", JSONObject(properties))
            put("confidence", confidence)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): KnowledgeEntity {
            val properties = mutableMapOf<String, Any>()
            val propsJson = json.getJSONObject("properties")
            propsJson.keys().forEach { key ->
                properties[key] = propsJson.get(key)
            }
            
            return KnowledgeEntity(
                id = json.getString("id"),
                type = EntityType.valueOf(json.getString("type")),
                name = json.getString("name"),
                properties = properties,
                confidence = json.getDouble("confidence").toFloat(),
                createdAt = json.getLong("createdAt"),
                updatedAt = json.getLong("updatedAt")
            )
        }
    }
}

data class KnowledgeRelation(
    val id: String,
    val fromEntityId: String,
    val toEntityId: String,
    val type: RelationType,
    val confidence: Float,
    val properties: Map<String, Any>,
    val createdAt: Long
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("fromEntityId", fromEntityId)
            put("toEntityId", toEntityId)
            put("type", type.name)
            put("confidence", confidence)
            put("properties", JSONObject(properties))
            put("createdAt", createdAt)
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): KnowledgeRelation {
            val properties = mutableMapOf<String, Any>()
            val propsJson = json.getJSONObject("properties")
            propsJson.keys().forEach { key ->
                properties[key] = propsJson.get(key)
            }
            
            return KnowledgeRelation(
                id = json.getString("id"),
                fromEntityId = json.getString("fromEntityId"),
                toEntityId = json.getString("toEntityId"),
                type = RelationType.valueOf(json.getString("type")),
                confidence = json.getDouble("confidence").toFloat(),
                properties = properties,
                createdAt = json.getLong("createdAt")
            )
        }
    }
}

class KnowledgeGraph {
    private val nodes = mutableMapOf<String, KnowledgeEntity>()
    private val edges = mutableMapOf<String, MutableList<Pair<String, KnowledgeRelation>>>()
    
    fun addNode(entity: KnowledgeEntity) {
        nodes[entity.id] = entity
        if (edges[entity.id] == null) {
            edges[entity.id] = mutableListOf()
        }
    }
    
    fun addEdge(from: KnowledgeEntity, to: KnowledgeEntity, relation: KnowledgeRelation) {
        edges[from.id]?.add(Pair(to.id, relation))
    }
    
    fun findPath(from: KnowledgeEntity, to: KnowledgeEntity, maxDepth: Int): List<KnowledgePath>? {
        val queue = mutableListOf(listOf(from))
        val visited = mutableSetOf(from.id)
        
        while (queue.isNotEmpty()) {
            val path = queue.removeAt(0)
            
            if (path.last().id == to.id) {
                return path.zipWithNext().mapIndexed { index, (fromEntity, toEntity) ->
                    KnowledgePath(
                        entities = listOf(fromEntity, toEntity),
                        relations = emptyList(), // Would need to lookup actual relation
                        depth = index + 1
                    )
                }
            }
            
            if (path.size < maxDepth) {
                val current = path.last()
                edges[current.id]?.forEach { (targetId, relation) ->
                    if (targetId !in visited) {
                        visited.add(targetId)
                        nodes[targetId]?.let { targetEntity ->
                            queue.add(path + targetEntity)
                        }
                    }
                }
            }
        }
        
        return null
    }
}

data class KnowledgePath(
    val entities: List<KnowledgeEntity>,
    val relations: List<KnowledgeRelation>,
    val depth: Int
)

data class RelatedEntity(
    val entity: KnowledgeEntity,
    val relation: KnowledgeRelation,
    val distance: Int,
    val confidence: Float
)

data class KnowledgeGraphStatistics(
    val entityCount: Int = 0,
    val relationCount: Int = 0,
    val entityTypes: Map<EntityType, Int> = emptyMap(),
    val relationTypes: Map<RelationType, Int> = emptyMap(),
    val lastUpdateTime: Long = 0L
)

data class KnowledgeExpansionResult(
    val success: Boolean,
    val entitiesAdded: Int = 0,
    val relationsAdded: Int = 0,
    val processingTime: Long = 0L,
    val error: String? = null
)

data class TextExtractionResult(
    val entities: List<ExtractedEntity>,
    val relations: List<ExtractedRelation>,
    val processingTime: Long
)

data class ExtractedEntity(
    val id: String,
    val type: EntityType,
    val name: String,
    val properties: Map<String, Any>,
    val confidence: Float
)

data class ExtractedRelation(
    val fromEntityId: String,
    val toEntityId: String,
    val type: RelationType,
    val confidence: Float,
    val properties: Map<String, Any>
)