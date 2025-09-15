package com.aminpinjari.screentranslater.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aminpinjari.screentranslater.repo.TranslationRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TranslationViewModel(private val repository: TranslationRepository) : ViewModel() {

    // loading state for showing spinner while downloading
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    // trigger flow: emits every time translation should run (even same language repeatedly)
    private val _translationTrigger = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val translationTrigger: SharedFlow<String> = _translationTrigger.asSharedFlow()

    /**
     * Request a target language. This will:
     * - if model already ready -> emit trigger immediately
     * - otherwise download model (suspends) and emit trigger only if this request becomes active
     */
    fun setLanguage(lang: String) {
        viewModelScope.launch {
            // fast path: already have model
            if (repository.hasModelFor(lang)) {
                // still emit trigger so UI re-translates even for same language
                _translationTrigger.tryEmit(lang)
                return@launch
            }

            _loading.value = true
            val applied = repository.prepareLanguage(lang) // suspend
            _loading.value = false

            if (applied) {
                _translationTrigger.tryEmit(lang)
            }
            // else: failed or superseded — do nothing
        }
    }

    fun translate(text: String, callback: (String) -> Unit) {
        repository.translate(text, callback)
    }

    class Factory(private val repository: TranslationRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TranslationViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return TranslationViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}