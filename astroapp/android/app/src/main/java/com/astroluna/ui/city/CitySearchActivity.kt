package com.astroluna.ui.city

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class CitySearchActivity : ComponentActivity() {
    private val viewModel: CityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CSCScreen(viewModel) { city ->
                        val intent = Intent().apply {
                            // Format: City, State, Country
                            val state = viewModel.uiState.value.selectedState?.name ?: ""
                            val country = viewModel.uiState.value.selectedCountry?.name ?: ""
                            val fullName = "${city.name}, $state, $country"

                            putExtra("name", fullName)
                            putExtra("city", city.name)
                            putExtra("state", state)
                            putExtra("country", country)
                            putExtra("lat", city.lat)
                            putExtra("lon", city.lon)
                            // Pass timezone ID from csc.db (ex: "Asia/Kolkata")
                            putExtra("timezoneId", city.timezone ?: "")
                        }
                        setResult(RESULT_OK, intent)
                        finish()
                    }
                }
            }
        }
    }
}

@Composable
fun CSCScreen(viewModel: CityViewModel, onFinalSelect: (LocationItem) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    // Logic for City Filter (Search within selected state)
    var cityQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Select Location", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))

        // Country Selector
        DropdownSelector(
            label = "Country",
            selectedItem = uiState.selectedCountry?.name,
            items = uiState.countries,
            onSelect = { viewModel.onCountrySelected(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // State Selector
        if (uiState.selectedCountry != null) {
            DropdownSelector(
                label = "State",
                selectedItem = uiState.selectedState?.name,
                items = uiState.states,
                onSelect = { viewModel.onStateSelected(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // City Search/List
        if (uiState.selectedState != null) {
            Text("Select City", style = MaterialTheme.typography.labelLarge)

            OutlinedTextField(
                value = cityQuery,
                onValueChange = { cityQuery = it },
                label = { Text("Search City") },
                leadingIcon = { Icon(Icons.Default.Search, "") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )

            val filteredCities = if (cityQuery.isBlank()) uiState.cities else uiState.cities.filter {
                it.name.contains(cityQuery, ignoreCase = true)
            }

            if (uiState.isLoading) {
                 CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filteredCities) { city ->
                    Text(
                        text = city.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFinalSelect(city) }
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Divider()
                }
            }
        }
    }
}

@Composable
fun DropdownSelector(
    label: String,
    selectedItem: String?,
    items: List<LocationItem>,
    onSelect: (LocationItem) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .background(Color.White, MaterialTheme.shapes.small)
                .clickable { if (items.isNotEmpty()) expanded = true }
        ) {
            OutlinedTextField(
                value = selectedItem ?: "Select $label",
                onValueChange = {},
                readOnly = true,
                enabled = false,
                modifier = Modifier.fillMaxWidth().clickable { if (items.isNotEmpty()) expanded = true },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, "") },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 300.dp)
            ) {
                // Compose DropdownMenu isn't virtualization friendly, so huge lists lag.
                // For Countries/States it's okay (usually < 300).
                items.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.name) },
                        onClick = {
                            onSelect(item)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
