package com.aminpinjari.screentranslater.translate

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.LinearLayout
import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

object LanguagePickerDialog {

    private val languages = TranslateLanguage.getAllLanguages().toList()
    private val languageNames = languages.map { code ->
        val locale = Locale.forLanguageTag(code)
        locale.getDisplayLanguage(locale) // autonym
    }

    fun show(
        context: Context,
        onLanguageSelected: (String, Boolean) -> Unit
    ) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val checkBox = CheckBox(context).apply {
            text = "Keep translating on navigation"
        }
        container.addView(checkBox)

        AlertDialog.Builder(context)
            .setTitle("Choose Language")
            .setItems(languageNames.toTypedArray()) { dialog, which ->
                val selectedLang = languages[which]
                val keep = checkBox.isChecked
                onLanguageSelected(selectedLang, keep)
                dialog.dismiss()
            }
            .setView(container)
            .show()
    }
}
