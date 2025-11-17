package com.ailive.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ailive.ai.llm.LLMBridge
import com.ailive.memory.database.entities.FactCategory
import com.ailive.memory.database.entities.LongTermFactEntity
import com.ailive.memory.managers.LongTermMemoryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Memory management screen.
 * Handles the business logic for displaying, adding, and deleting facts.
 *
 * Updated to use LongTermMemoryManager (unified memory system with semantic search).
 */
class MemoryViewModel(private val longTermMemory: LongTermMemoryManager) : ViewModel() {

    private val _facts = MutableStateFlow<List<LongTermFactEntity>>(emptyList())
    val facts: StateFlow<List<LongTermFactEntity>> = _facts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadAllFacts()
    }

    /**
     * Loads all facts from the database.
     */
    fun loadAllFacts() {
        viewModelScope.launch {
            _isLoading.value = true
            _facts.value = longTermMemory.getAllFacts()
            _isLoading.value = false
        }
    }

    /**
     * Adds a new fact to the memory.
     * For simplicity, new facts are categorized as OTHER.
     */
    fun addFact(factText: String) {
        viewModelScope.launch {
            _isLoading.value = true
            longTermMemory.learnFact(
                category = FactCategory.OTHER,
                factText = factText,
                extractedFrom = "manual_entry"
            )
            // Refresh the list after adding
            loadAllFacts()
        }
    }

    /**
     * Deletes a fact from memory.
     */
    fun deleteFact(id: String) {
        viewModelScope.launch {
            _isLoading.value = true
            longTermMemory.deleteFact(id)
            // Refresh the list after deleting
            loadAllFacts()
        }
    }
}

/**
 * Factory for creating a MemoryViewModel with a LongTermMemoryManager dependency.
 * This is the standard way to provide parameters to a ViewModel.
 */
class MemoryViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MemoryViewModel::class.java)) {
            // Manually construct the dependencies for the ViewModel
            val llmBridge = LLMBridge() // Assuming a singleton or simple instantiation
            val longTermMemory = LongTermMemoryManager(application, llmBridge)
            @Suppress("UNCHECKED_CAST")
            return MemoryViewModel(longTermMemory) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
