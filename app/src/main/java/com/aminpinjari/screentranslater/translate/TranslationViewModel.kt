package com.aminpinjari.screentranslater.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aminpinjari.screentranslater.repo.TranslationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class TranslationViewModel(private val repo: TranslationRepository) : ViewModel() {
    private val _progress = MutableStateFlow(0) // download %
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _translationTrigger = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val translationTrigger: SharedFlow<String> = _translationTrigger

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            _loading.value = true

            repo.prepareLanguage(
                lang,
                onReady = { success ->
                    _loading.value = false
                    if (success) _translationTrigger.tryEmit(lang)
                }
            )
        }
    }

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
