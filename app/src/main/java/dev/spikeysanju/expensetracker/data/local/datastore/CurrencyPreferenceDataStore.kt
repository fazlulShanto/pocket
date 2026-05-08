package dev.spikeysanju.expensetracker.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.spikeysanju.expensetracker.utils.SupportedCurrency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CurrencyPreferenceDataStore(context: Context) : CurrencyPreference {

    private val dataStore = context.themePrefDataStore

    override val selectedCurrency: Flow<SupportedCurrency>
        get() = dataStore.data.map { preferences ->
            SupportedCurrency.fromCode(preferences[CURRENCY_KEY])
        }

    override suspend fun saveSelectedCurrency(currency: SupportedCurrency) {
        dataStore.edit { preferences ->
            preferences[CURRENCY_KEY] = currency.code
        }
    }

    companion object {
        private val CURRENCY_KEY = stringPreferencesKey("selected_currency")
    }
}

interface CurrencyPreference {
    val selectedCurrency: Flow<SupportedCurrency>
    suspend fun saveSelectedCurrency(currency: SupportedCurrency)
}