package com.astroluna.ui.city

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.astroluna.data.repository.CSCRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LocationItem(
    val id: String,
    val name: String,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val timezone: String? = null
)

data class CSCUiState(
    val countries: List<LocationItem> = emptyList(),
    val states: List<LocationItem> = emptyList(),
    val cities: List<LocationItem> = emptyList(),

    val selectedCountry: LocationItem? = null,
    val selectedState: LocationItem? = null,
    val selectedCity: LocationItem? = null,

    val isLoading: Boolean = false
)

class CityViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CSCRepository(application)

    private val _uiState = MutableStateFlow(CSCUiState())
    val uiState: StateFlow<CSCUiState> = _uiState.asStateFlow()

    init {
        loadCountries()
    }

    private fun loadCountries() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val list = repository.getCountries().map {
                LocationItem(it["id"] ?: "", it["name"] ?: "")
            }
            _uiState.value = _uiState.value.copy(countries = list, isLoading = false)

            // Default: Select India
            val india = list.find { it.name.equals("India", ignoreCase = true) }
            if (india != null) {
                selectDefaultCountryAndState(india)
            }
        }
    }

    private fun selectDefaultCountryAndState(country: LocationItem) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedCountry = country,
                isLoading = true
            )
            val states = repository.getStates(country.id).map {
                LocationItem(it["id"] ?: "", it["name"] ?: "")
            }
            _uiState.value = _uiState.value.copy(states = states, isLoading = false)

            // Default: Select Tamil Nadu
            val tamilNadu = states.find { it.name.equals("Tamil Nadu", ignoreCase = true) }
            if (tamilNadu != null) {
                onStateSelected(tamilNadu)
            }
        }
    }

    fun onCountrySelected(country: LocationItem) {
        if (uiState.value.selectedCountry == country) return
        _uiState.value = _uiState.value.copy(
            selectedCountry = country,
            selectedState = null,
            selectedCity = null,
            states = emptyList(),
            cities = emptyList(),
            isLoading = true
        )
        viewModelScope.launch {
            val list = repository.getStates(country.id).map {
                LocationItem(it["id"] ?: "", it["name"] ?: "")
            }
            _uiState.value = _uiState.value.copy(states = list, isLoading = false)
        }
    }

    fun onStateSelected(state: LocationItem) {
        if (uiState.value.selectedState == state) return
        _uiState.value = _uiState.value.copy(
            selectedState = state,
            selectedCity = null,
            cities = emptyList(),
            isLoading = true
        )
        viewModelScope.launch {
            val list = repository.getCities(state.id).map {
                LocationItem(
                    id = it["id"] ?: "",
                    name = it["name"] ?: "",
                    lat = it["latitude"]?.toDoubleOrNull() ?: 0.0,
                    lon = it["longitude"]?.toDoubleOrNull() ?: 0.0,
                    timezone = it["timezone"]
                )
            }
            _uiState.value = _uiState.value.copy(cities = list, isLoading = false)
        }
    }

    fun onCitySelected(city: LocationItem) {
        _uiState.value = _uiState.value.copy(selectedCity = city)
    }
}
