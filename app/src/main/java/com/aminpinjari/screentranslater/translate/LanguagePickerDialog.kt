package com.aminpinjari.screentranslater.translate

import android.app.AlertDialog
import android.content.Context
import com.google.mlkit.nl.translate.TranslateLanguage

object LanguagePickerDialog {

    private val languages = TranslateLanguage.getAllLanguages().toList()
    private val languageNames = languages.map { it.replaceFirstChar { c -> c.uppercase() } }

    fun show(context: Context, onLanguageSelected: (String) -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Choose Language")
            .setItems(languageNames.toTypedArray()) { _, which ->
                val selectedLang = languages[which]
                onLanguageSelected(selectedLang)
            }
            .show()
    }
}
