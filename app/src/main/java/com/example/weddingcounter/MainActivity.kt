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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterApp(viewModel: CounterViewModel) {
    var selectedUser by remember { mutableStateOf("Klara") }
    val counters by viewModel.counters.observeAsState(emptyMap())

    Scaffold(
        topBar = { TopAppBar(title = { Text("Counter App") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
        }
    }
}


@Composable
fun UserCounter(user: String, counters: Map<String, Int>, viewModel: CounterViewModel) {
    val weddingCount = counters["Wedding"] ?: 0
    val fatnessCount = counters["Fatness"] ?: 0

    Column(
        modifier = Modifier.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CounterItem(
            label = "Wedding",
            count = weddingCount,
            onIncrement = { viewModel.updateCounter(user, "Wedding", 1) },
            onDecrement = { viewModel.updateCounter(user, "Wedding", -1) }
        )
        CounterItem(
            label = "Fatness",
            count = fatnessCount,
            onIncrement = { viewModel.updateCounter(user, "Fatness", 1) },
            onDecrement = { viewModel.updateCounter(user, "Fatness", -1) }
        )
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

        private val _dateEntries = MutableLiveData<List<DateEntry>>()
        val dateEntries: LiveData<List<DateEntry>> = _dateEntries


        init {

            getCounters("Klara")
        }

        fun getCounters(user: String) {
            val currentDate = System.currentTimeMillis().toDay()
            val docRef = firestore.collection("counters").document(user).collection("daily_counters").document(currentDate)

            docRef.get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val data = document.data ?: emptyMap()
                        val counters = mapOf(
                            ("Wedding" to (data["Wedding"] as? Long)?.toInt() ?: 0) as Pair<String, Int>,
                            ("Fatness" to (data["Fatness"] as? Long)?.toInt() ?: 0) as Pair<String, Int>
                        )
                        _counters.value = counters
                    } else {
                        _counters.value = mapOf("Wedding" to 0, "Fatness" to 0)
                    }
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
                        val wedding = document.getLong("Wedding")?.toInt() ?: 0
                        val fatness = document.getLong("Fatness")?.toInt() ?: 0
                        DateEntry(date, mapOf("Wedding" to wedding, "Fatness" to fatness))
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
            DateEntry(
                date = entry.date,
                weddingCount = entry.counters["Wedding"] ?: 0,
                fatnessCount = entry.counters["Fatness"] ?: 0
            )
            Divider() // Optional: Add dividers between entries
        }
    }

    LaunchedEffect(user) {
        viewModel.fetchDateEntries(user)
    }
}

@Composable
fun DateEntry(date: String, weddingCount: Int, fatnessCount: Int) {
    Column(
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Text(text = "Date: $date")
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "Wedding: $weddingCount")
        Text(text = "Fatness: $fatnessCount")
    }
}
