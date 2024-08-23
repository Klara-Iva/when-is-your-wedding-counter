@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.weddingcounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val viewModel: CounterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.getCounters("Klara")
        setContent {
            CounterApp(viewModel)
        }
    }
}

@Composable
fun CounterApp(viewModel: CounterViewModel) {
    var selectedUser by remember { mutableStateOf("Klara") }
    var showDialog by remember { mutableStateOf(false) }
    val counters by viewModel.counters.observeAsState(emptyMap())

    LaunchedEffect(selectedUser) {
        viewModel.fetchCounterNames(selectedUser)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Counter App") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = { showDialog = true }) {
                Text("Add New Counter")
            }
            Row {
                Button(onClick = {
                    selectedUser = "Klara"
                    viewModel.getCounters("Klara")
                }) {
                    Text("Klara")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = {
                    selectedUser = "Zoltan"
                    viewModel.getCounters("Zoltan")
                }) {
                    Text("Zoltan")
                }
            }
            Text(text = selectedUser)
            Spacer(modifier = Modifier.height(16.dp))
            UserCounter(
                user = selectedUser,
                counters = counters,
                viewModel = viewModel
            )
            Spacer(modifier = Modifier.height(16.dp))
            DateEntries(viewModel = viewModel, user = selectedUser)
            Spacer(modifier = Modifier.height(16.dp))

        }

        AddCounterDialog(
            isOpen = showDialog,
            onDismiss = { showDialog = false },
            onAdd = { newCounterName ->
                viewModel.addNewCounter(selectedUser, newCounterName)
                showDialog = false
            }
        )
    }
}

@Composable
fun UserCounter(user: String, counters: Map<String, Int>, viewModel: CounterViewModel) {
    Column(
        modifier = Modifier.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        counters.forEach { (label, count) ->
            CounterItem(
                label = label,
                count = count,
                onIncrement = { viewModel.updateCounter(user, label, 1) },
                onDecrement = { viewModel.updateCounter(user, label, -1) }
            )
        }
    }
}

@Composable
fun CounterItem(label: String, count: Int, onIncrement: () -> Unit, onDecrement: () -> Unit) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = onDecrement) {
            Text("-")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "$label: $count", modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = onIncrement) {
            Text("+")
        }
    }
}

data class DateEntry(
    val date: String,
    val counters: Map<String, Int>
)

class CounterViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val firestore = FirebaseFirestore.getInstance()

    private val _counters = MutableLiveData<Map<String, Int>>()
    val counters: LiveData<Map<String, Int>> = _counters

    private val _counterNames = MutableLiveData<List<String>>()
    val counterNames: LiveData<List<String>> = _counterNames

    private val _dateEntries = MutableLiveData<List<DateEntry>>()
    val dateEntries: LiveData<List<DateEntry>> = _dateEntries

    init {
        fetchCounterNames("Klara") // Initialize with a default user or handle in the UI
    }

    fun fetchCounterNames(user: String) {
        val counterNamesRef = firestore.collection("counters").document(user)

        counterNamesRef.get()
            .addOnSuccessListener { document ->
                // Check if document exists and retrieve the names field
                if (document.exists()) {
                    val names = document.get("counter_names") as? List<String> ?: emptyList()
                    _counterNames.value = names
                    getCounters(user) // Refresh counters based on fetched names
                } else {
                    // Handle the case where the document does not exist
                    _counterNames.value = emptyList()
                    getCounters(user) // Refresh counters based on empty list
                }
            }
            .addOnFailureListener { exception ->
                // Handle any errors that occurred during the fetch
                _counterNames.value = emptyList() // Handle error scenario
                exception.printStackTrace()
            }
    }


    fun getCounters(user: String) {
        val currentDate = System.currentTimeMillis().toDay()
        val docRef = firestore.collection("counters").document(user).collection("daily_counters").document(currentDate)

        docRef.get()
            .addOnSuccessListener { document ->
                val data = document.data ?: emptyMap()
                val counterNames = _counterNames.value ?: emptyList()
                val counters = counterNames.associateWith { name ->
                    // Safely get the value from data and convert it to Int
                    (data[name] as? Long)?.toInt() ?: 0
                }
                _counters.value = counters
            }
            .addOnFailureListener {
                _counters.value = emptyMap() // Handle error
            }
    }

    fun fetchDateEntries(user: String) {
        firestore.collection("counters").document(user).collection("daily_counters")
            .get()
            .addOnSuccessListener { documents ->
                val dateEntries = documents.mapNotNull { document ->
                    val date = document.id
                    val counters = document.data?.mapValues { (key, value) -> (value as? Long)?.toInt() ?: 0 } ?: emptyMap()
                    DateEntry(date, counters)
                }
                _dateEntries.value = dateEntries
            }
            .addOnFailureListener {
                _dateEntries.value = emptyList() // Handle error
            }
    }

    fun updateCounter(user: String, type: String, increment: Int) {
        val currentDate = System.currentTimeMillis().toDay()
        val docRef = firestore.collection("counters").document(user).collection("daily_counters").document(currentDate)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val newCount = (snapshot.getLong(type) ?: 0L) + increment
            transaction.set(docRef, mapOf(type to newCount), SetOptions.merge())
        }.addOnSuccessListener {
            getCounters(user) // Refresh the counters
        }.addOnFailureListener {
            // Handle failure
        }
    }

    fun addNewCounter(user: String, counterName: String) {
        val counterNamesRef = firestore.collection("counters").document(user)
        val currentDate = System.currentTimeMillis().toDay()
        val dailyCountersRef = firestore.collection("counters").document(user).collection("daily_counters").document(currentDate)

        // First, read the current list of counter names
        counterNamesRef.get().addOnSuccessListener { counterNamesDoc ->
            val existingNames = counterNamesDoc.get("counter_names") as? List<String> ?: emptyList()
            val updatedNames = existingNames.toMutableList()

            // Add new counter name to the list if it doesn't already exist
            if (counterName !in updatedNames) {
                updatedNames.add(counterName)
                println("Updated names: $updatedNames")

                // Update the counter names
                counterNamesRef.update("counter_names", updatedNames)
                    .addOnSuccessListener {
                        println("Counter names updated successfully")

                        // Initialize the counter for today
                        dailyCountersRef.get().addOnSuccessListener { dailyCountersDoc ->
                            val newCounters = dailyCountersDoc.data?.toMutableMap() ?: mutableMapOf()
                            if (counterName !in newCounters) {
                                newCounters[counterName] = 0 // Initialize to 0
                                dailyCountersRef.set(newCounters, SetOptions.merge())
                                    .addOnSuccessListener {
                                        println("Daily counter initialized successfully")
                                        fetchCounterNames(user) // Refresh counter names and counters
                                    }
                                    .addOnFailureListener { exception ->
                                        println("Failed to initialize daily counter")
                                        exception.printStackTrace()
                                    }
                            } else {
                                println("Counter already exists for today")
                                fetchCounterNames(user) // Refresh counter names and counters
                            }
                        }
                            .addOnFailureListener { exception ->
                                println("Failed to fetch daily counters")
                                exception.printStackTrace()
                            }
                    }
                    .addOnFailureListener { exception ->
                        println("Failed to update counter names")
                        exception.printStackTrace()
                    }
            } else {
                // If the counter name already exists, just initialize the daily counter
                dailyCountersRef.get().addOnSuccessListener { dailyCountersDoc ->
                    val newCounters = dailyCountersDoc.data?.toMutableMap() ?: mutableMapOf()
                    if (counterName !in newCounters) {
                        newCounters[counterName] = 0 // Initialize to 0
                        dailyCountersRef.set(newCounters, SetOptions.merge())
                            .addOnSuccessListener {
                                println("Daily counter initialized successfully")
                                fetchCounterNames(user) // Refresh counter names and counters
                            }
                            .addOnFailureListener { exception ->
                                println("Failed to initialize daily counter")
                                exception.printStackTrace()
                            }
                    } else {
                        println("Counter already exists for today")
                        fetchCounterNames(user) // Refresh counter names and counters
                    }
                }
                    .addOnFailureListener { exception ->
                        println("Failed to fetch daily counters")
                        exception.printStackTrace()
                    }
            }
        }
            .addOnFailureListener { exception ->
                println("Failed to fetch counter names")
                exception.printStackTrace()
            }
    }



}

fun Long.toDay(): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return dateFormat.format(Date(this))
}

@Composable
fun DateEntries(viewModel: CounterViewModel, user: String) {
    val dateEntries by viewModel.dateEntries.observeAsState(emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(dateEntries) { entry ->
            DateEntryView(
                date = entry.date,
                counters = entry.counters
            )
            Divider() // Optional: Add dividers between entries
        }
    }

    LaunchedEffect(user) {
        viewModel.fetchDateEntries(user)
    }
}

@Composable
fun DateEntryView(date: String, counters: Map<String, Int>) {
    Column(
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Text(text = "Date: $date")
        Spacer(modifier = Modifier.height(4.dp))
        counters.forEach { (label, count) ->
            Text(text = "$label: $count")
        }
    }
}

@Composable
fun AddCounterDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var newCounterName by remember { mutableStateOf("") }

    if (isOpen) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add New Counter") },
            text = {
                Column {
                    Text("Enter the name for the new counter:")
                    TextField(
                        value = newCounterName,
                        onValueChange = { newCounterName = it },
                        label = { Text("Counter Name") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newCounterName.isNotBlank()) {
                        onAdd(newCounterName)
                        onDismiss()
                    }
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                Button(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}
