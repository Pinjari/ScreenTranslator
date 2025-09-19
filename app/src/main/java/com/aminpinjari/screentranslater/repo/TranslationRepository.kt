package com.aminpinjari.screentranslater.repo

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Thin wrapper around MLKit translation client.
 * - prepareLanguage downloads the model if needed and returns via callback when ready.
 * - translate calls the currently prepared Translator instance (if available).
 *
 * This file intentionally stays simple. ViewModel is responsible for controlling when to call prepareLanguage().
 */
class TranslationRepository(private val context: Context) {

    private var translator: Translator? = null
    private var currentLang: String? = null

    /**
     * Prepare given target language; if different from current, close existing translator and
     * obtain a new one. Calls onReady(true) only when model/client ready.
     */
    fun prepareLanguage(
        lang: String,
        onReady: (Boolean) -> Unit
    ) {
        if (lang != currentLang) {
            translator?.close()
            translator = null
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH) // adjust if you want dynamic source
            .setTargetLanguage(lang)
            .build()

        val client = Translation.getClient(options)
        val conditions = DownloadConditions.Builder().build()

        client.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                translator = client
                currentLang = lang
                onReady(true)
            }
            .addOnFailureListener {
                onReady(false)
            }
    }

    /**
     * Translate the given string using the prepared translator.
     * If translator is null, fallback to returning original text.
     */
    fun translate(text: String, onResult: (String) -> Unit) {
        translator?.translate(text)
            ?.addOnSuccessListener { translated -> onResult(translated) }
            ?.addOnFailureListener { onResult(text) }
            ?: onResult(text)
    }
}
