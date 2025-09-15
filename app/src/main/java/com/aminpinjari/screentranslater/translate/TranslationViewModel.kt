package com.aminpinjari.screentranslater.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aminpinjari.screentranslater.repo.TranslationRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TranslationViewModel(private val repository: TranslationRepository) : ViewModel() {

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _translationTrigger = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val translationTrigger: SharedFlow<String> = _translationTrigger.asSharedFlow()

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            _loading.value = true
            repository.prepareLanguage(lang) { success ->
                _loading.value = false
                if (success) {
                    // emit trigger every time (so multiple selections work)
                    _translationTrigger.tryEmit(lang)
                }
            }
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
