package com.aminpinjari.screentranslater.translate

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.lifecycle.LifecycleCoroutineScope
import com.aminpinjari.screentranslater.R
import com.aminpinjari.screentranslater.translate.TranslationViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.google.android.material.bottomnavigation.BottomNavigationView

class TranslationManager(
    private val activity: Activity,
    private val viewModel: TranslationViewModel,
    private val lifecycleScope: LifecycleCoroutineScope
) {

    private val originalMenuTitles = mutableMapOf<Int, String>()
    private var originalToolbarTitle: String? = null

    fun startObserving(actionBar: ActionBar?, bottomNav: BottomNavigationView?) {
        lifecycleScope.launch {
            viewModel.translationTrigger.collectLatest {
                translateAll(actionBar, bottomNav)
            }
        }
    }

    private fun translateAll(actionBar: ActionBar?, bottomNav: BottomNavigationView?) {
        // activity root views
        traverseViews(activity.findViewById(android.R.id.content))

        // toolbar
        actionBar?.let { translateToolbar(it) }

        // bottom nav
        bottomNav?.let { translateBottomNav(it) }
    }

    private fun translateToolbar(actionBar: ActionBar) {
        val current = actionBar.title?.toString() ?: return
        if (originalToolbarTitle == null) originalToolbarTitle = current

        val original = originalToolbarTitle ?: current
        viewModel.translate(original) { translated ->
            activity.runOnUiThread { actionBar.title = translated }
        }
    }

    private fun translateBottomNav(bottomNav: BottomNavigationView) {
        val menu = bottomNav.menu
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val id = item.itemId
            val original = originalMenuTitles[id] ?: item.title.toString().also {
                originalMenuTitles[id] = it
            }
            viewModel.translate(original) { translated ->
                activity.runOnUiThread { item.title = translated }
            }
        }
    }

    private fun traverseViews(view: View) {
        when (view) {
            is ViewGroup -> {
                for (i in 0 until view.childCount) traverseViews(view.getChildAt(i))
            }
            is TextView -> {
                val tagKey = R.id.original_text
                val original = (view.getTag(tagKey) as? String) ?: view.text.toString().also {
                    view.setTag(tagKey, it)
                }
                viewModel.translate(original) { translated ->
                    view.post { view.text = translated }
                }
            }
        }
    }
}
