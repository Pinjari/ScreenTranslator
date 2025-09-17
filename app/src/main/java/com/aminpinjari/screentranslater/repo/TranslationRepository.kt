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
        if (lang != currentLang) {
            translator?.close()
            translator = null
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
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

    fun translate(text: String, onResult: (String) -> Unit) {
        translator?.translate(text)
            ?.addOnSuccessListener { translated -> onResult(translated) }
            ?.addOnFailureListener { onResult(text) }
            ?: onResult(text)
    }
}
