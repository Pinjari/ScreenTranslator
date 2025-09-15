package com.aminpinjari.screentranslater.translate

import android.app.AlertDialog
import android.content.Context
import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.*

object LanguagePickerDialog {

    private val codes = TranslateLanguage.getAllLanguages().toList()
    private val names = codes.map { code ->
        try {
            val locale = Locale.forLanguageTag(code)
            val display = locale.getDisplayLanguage(locale)
            if (display.isNullOrBlank()) code else display.replaceFirstChar { it.titlecase() }
        } catch (e: Exception) {
            code
        }
    }

    fun show(context: Context, onLanguageSelected: (String) -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Choose language")
            .setItems(names.toTypedArray()) { _, index ->
                onLanguageSelected(codes[index])
            }
            .show()
    }
}
