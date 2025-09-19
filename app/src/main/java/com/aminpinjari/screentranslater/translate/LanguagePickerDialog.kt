package com.aminpinjari.screentranslater.translate

import android.app.AlertDialog
import android.content.Context
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ArrayAdapter
import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

/**
 * Language picker dialog:
 * - shows language names in their native display language (autonym)
 * - contains a "Keep translating on navigation" checkbox
 * - returns (languageCode, keepBoolean)
 *
 * Keep it UI-simple and consistent with your previous code.
 */
object LanguagePickerDialog {

    private val languages = TranslateLanguage.getAllLanguages().toList()
    private val languageNames = languages.map { code ->
        val locale = Locale.forLanguageTag(code)
        // Use autonym (display name in native language) as requested
        locale.getDisplayLanguage(locale)
    }

    fun show(
        context: Context,
        onLanguageSelected: (String, Boolean) -> Unit
    ) {
        // Root vertical layout
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 30, 40, 10)
        }

        val checkBox = CheckBox(context).apply {
            text = "Keep translating on navigation"
        }
        container.addView(checkBox)

        // Use a ListView (simple) to show many languages reliably
        val listView = ListView(context).apply {
            adapter = ArrayAdapter(
                context,
                android.R.layout.simple_list_item_1,
                languageNames
            )
        }
        container.addView(listView)

        // Checkbox below the list
        val dialog = AlertDialog.Builder(context)
            .setTitle("Choose Language")
            .setView(container)
            .create()

        // Handle language selection
        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedLang = languages[position]
            val keep = checkBox.isChecked
            onLanguageSelected(selectedLang, keep)
            dialog.dismiss()
        }

        dialog.show()
    }
}
