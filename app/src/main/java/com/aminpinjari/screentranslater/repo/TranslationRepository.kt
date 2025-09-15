package com.aminpinjari.screentranslater.repo

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

class TranslationRepository(private val context: Context) {

    private var translator: Translator? = null
    private var currentLang: String? = null

    fun prepareLanguage(lang: String, onReady: (Boolean) -> Unit) {
        // if language changed, reset translator
        if (lang != currentLang) {
            translator?.close()
            translator = null
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH) // adjust source if needed
            .setTargetLanguage(lang)
            .build()

        val client = Translation.getClient(options)
        val conditions = DownloadConditions.Builder()
            .requireWifi() // only download on WiFi
            .build()

        client.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                translator = client
                currentLang = lang
                onReady(true)
            }
            .addOnFailureListener {
                onReady(false) // ensure spinner hides
            }
            .addOnCanceledListener {
                onReady(false) // ensure spinner hides
            }
    }

    fun translate(text: String, onResult: (String) -> Unit) {
        val client = translator
        if (client == null) {
            onResult(text) // fallback if not ready
            return
        }
        client.translate(text)
            .addOnSuccessListener { translated -> onResult(translated) }
            .addOnFailureListener { onResult(text) }
    }
}
