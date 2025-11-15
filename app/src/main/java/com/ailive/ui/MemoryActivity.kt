package com.ailive.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ailive.memory.database.entities.LongTermFactEntity
import com.ailive.ui.theme.AILiveTheme // Assuming a theme file exists
import com.ailive.ui.viewmodel.MemoryViewModel
import com.ailive.ui.viewmodel.MemoryViewModelFactory

class MemoryActivity : ComponentActivity() {
    private val viewModel: MemoryViewModel by viewModels {
        MemoryViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AILiveTheme {
                MemoryScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(viewModel: MemoryViewModel) {
    val facts by viewModel.facts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var newFactText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("AI Long-Term Memory") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Input for adding new facts
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newFactText,
                    onValueChange = { newFactText = it },
                    label = { Text("Enter a new fact") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (newFactText.isNotBlank()) {
                            viewModel.addFact(newFactText)
                            newFactText = ""
                        }
                    },
                    enabled = newFactText.isNotBlank() && !isLoading
                ) {
                    Text("Add")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Loading indicator and list
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading && facts.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(facts, key = { it.id }) { fact ->
                            FactCard(
                                fact = fact,
                                onDelete = { viewModel.deleteFact(fact.id) },
                                enabled = !isLoading
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FactCard(fact: LongTermFactEntity, onDelete: () -> Unit, enabled: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = fact.factText,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Fact"
                )
            }
        }
    }
}

// A placeholder theme. In a real app, this would be more complex.
@Composable
fun AILiveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(), // Using a default dark theme
        content = content
    )
}
