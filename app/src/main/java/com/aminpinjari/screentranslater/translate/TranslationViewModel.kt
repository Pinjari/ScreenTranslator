package com.aminpinjari.screentranslater.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aminpinjari.screentranslater.repo.TranslationRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel that triggers language selection and exposes simple APIs:
 * - setLanguage(lang): downloads/prepares model and emits translationTrigger when ready.
 * - translate(text, callback): translates the text via repository.
 *
 * Note: translationTrigger is a shared flow with String payload = language code.
 */
class TranslationViewModel(private val repo: TranslationRepository) : ViewModel() {

    private val _translationTrigger = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    val translationTrigger: SharedFlow<String> = _translationTrigger

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    // persistent language selection (if user checked "keep translating on navigation")
    private val _persistentLanguage = MutableStateFlow<String?>(null)
    val persistentLanguage: StateFlow<String?> = _persistentLanguage

    fun setPersistentLanguage(lang: String) {
        _persistentLanguage.value = lang
    }

    fun clearPersistentLanguage() {
        _persistentLanguage.value = null
    }

    /**
     * Start preparing language model and emit translationTrigger when model is ready.
     * Keeps UI-informed with _loading state.
     */
    fun setLanguage(lang: String) {
        viewModelScope.launch {
            _loading.value = true
            repo.prepareLanguage(lang) { success ->
                // ensure we switch back on main/coroutine and reliably emit the trigger
                viewModelScope.launch {
                    _loading.value = false
                    if (success) {
                        _translationTrigger.emit(lang) // suspend until delivered to collectors
                    }
                }
            }
        }
    }

    /**
     * Simple pass-through translate API. Callbacks arrive on MLKit background threads, not guaranteed
     * on main thread.
     */
    fun translate(text: String, callback: (String) -> Unit) {
        repo.translate(text, callback)
    }

    class Factory(private val repo: TranslationRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TranslationViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return TranslationViewModel(repo) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
