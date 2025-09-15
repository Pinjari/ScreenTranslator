package com.aminpinjari.screentranslater.repo

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class TranslationRepository(private val context: Context) {

    @Volatile
    private var translator: Translator? = null

    @Volatile
    private var currentLang: String? = null

    // token to ignore stale downloads when user quickly switches languages
    private val requestCounter = AtomicInteger(0)

    /**
     * Prepare translator for `lang`. Returns true if this request became the active translator.
     * If another prepareLanguage request started after this one, this function will discard the result and return false.
     *
     * Note: This is suspendable and must be called from a coroutine.
     */
    suspend fun prepareLanguage(lang: String): Boolean = withContext(Dispatchers.IO) {
        val myToken = requestCounter.incrementAndGet()

        // If already have translator for requested language -> fast path
        if (translator != null && currentLang == lang) return@withContext true

        // Close previous translator immediately when switching languages to free resources
        try { translator?.close() } catch (_: Exception) {}
        translator = null
        currentLang = null

        // Build a new client for requested language
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH) // change if you want to detect source dynamically
            .setTargetLanguage(lang)
            .build()

        val client = Translation.getClient(options)
        val conditions = DownloadConditions.Builder().requireWifi().build()

        // download model (suspends via await)
        try {
            client.downloadModelIfNeeded(conditions).await()
        } catch (e: Exception) {
            // download failed; close this client and return false
            try { client.close() } catch (_: Exception) {}
            return@withContext false
        }

        // If a later request started while we downloaded, discard this client
        if (requestCounter.get() != myToken) {
            try { client.close() } catch (_: Exception) {}
            return@withContext false
        }

        // Adopt this client as the active translator
        try { translator?.close() } catch (_: Exception) {}
        translator = client
        currentLang = lang
        return@withContext true
    }

    /** Returns true if translator currently exists for that language. */
    fun hasModelFor(lang: String): Boolean = (translator != null && currentLang == lang)

    /**
     * Translate text using active translator; falls back to the original text if translator missing/fails.
     */
    fun translate(text: String, onResult: (String) -> Unit) {
        val tx = translator
        if (tx == null) {
            onResult(text)
            return
        }
        tx.translate(text)
            .addOnSuccessListener { onResult(it) }
            .addOnFailureListener { onResult(text) }
    }

    fun close() {
        try { translator?.close() } catch (_: Exception) {}
        translator = null
        currentLang = null
        requestCounter.set(0)
    }
}
