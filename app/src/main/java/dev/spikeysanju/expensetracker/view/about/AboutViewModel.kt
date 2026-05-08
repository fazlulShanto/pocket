package dev.spikeysanju.expensetracker.view.about

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor() : ViewModel() {
    private val _url = MutableStateFlow("https://github.com/fazlulShanto/Expenso")
    val url: StateFlow<String> = _url

    fun launchLicense() {
        _url.value = "https://github.com/fazlulShanto/Expenso/blob/master/LICENSE"
    }

    fun launchRepository() {
        _url.value = "https://github.com/fazlulShanto/Expenso"
    }
}
