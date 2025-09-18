package com.aminpinjari.screentranslater.translate

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.aminpinjari.screentranslater.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TranslationManager(
    private val activity: AppCompatActivity,
    private val viewModel: TranslationViewModel,
    private val lifecycleScope: LifecycleCoroutineScope
) {
    private val originalMenuTitles = mutableMapOf<Int, String>()
    private var originalToolbarTitle: String? = null

    // 🔑 Cache for translations (original -> translated)
    private val translationCache = mutableMapOf<String, String>()

    fun startObserving(actionBar: ActionBar?, bottomNav: BottomNavigationView?) {
        lifecycleScope.launch {
            viewModel.translationTrigger.collectLatest {
                translateAll(actionBar, bottomNav)
                translateFragments()
            }
        }
    }

    fun translateCurrentScreen(actionBar: ActionBar?, bottomNav: BottomNavigationView?) {
        translateAll(actionBar, bottomNav)
        translateFragments()
    }

    fun restoreOriginals(actionBar: ActionBar?, bottomNav: BottomNavigationView?) {
        // restore toolbar
        actionBar?.title = originalToolbarTitle ?: actionBar?.title

        // restore menu
        bottomNav?.menu?.let { menu ->
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                originalMenuTitles[item.itemId]?.let { item.title = it }
            }
        }

        // restore fragment views
        val navHostFragment =
            activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main)
        navHostFragment?.childFragmentManager?.fragments?.forEach { frag ->
            frag.view?.let { restoreViewTexts(it) }
        }

        // clear cache after restoring
        translationCache.clear()
    }

    private fun translateAll(actionBar: ActionBar?, bottomNav: BottomNavigationView?) {
        traverseViews(activity.findViewById(android.R.id.content))
        actionBar?.let { translateToolbar(it) }
        bottomNav?.let { translateBottomNav(it) }
    }

    private fun translateFragments() {
        val navHostFragment =
            activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main)
        navHostFragment?.childFragmentManager?.fragments?.forEach { fragment ->
            fragment.view?.let { traverseViews(it) }
        }
    }

    private fun translateToolbar(actionBar: ActionBar) {
        val current = actionBar.title?.toString() ?: return
        if (originalToolbarTitle == null) originalToolbarTitle = current

        val original = originalToolbarTitle ?: current
        translateText(original) { translated ->
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
            translateText(original) { translated ->
                activity.runOnUiThread { item.title = translated }
            }
        }
    }

    private fun traverseViews(view: View) {
        when (view) {
            is ViewGroup -> {
                // Special case: RecyclerView
                if (view is RecyclerView) {
                    translateRecyclerView(view)
                }
                for (i in 0 until view.childCount) {
                    traverseViews(view.getChildAt(i))
                }
            }
            is TextView -> {
                val tagKey = R.id.original_text
                val original = (view.getTag(tagKey) as? String) ?: view.text.toString().also {
                    view.setTag(tagKey, it)
                }
                translateText(original) { translated ->
                    if (translated.isNotBlank()) {
                        view.post { view.text = translated }
                    }
                }
            }
        }
    }

    private fun translateRecyclerView(recyclerView: RecyclerView) {
        // Initial children
        for (i in 0 until recyclerView.childCount) {
            traverseViews(recyclerView.getChildAt(i))
        }

        // Re-translate on scroll (new views bound)
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                for (i in 0 until rv.childCount) {
                    traverseViews(rv.getChildAt(i))
                }
            }
        })

        // Also handle adapter data changes
        recyclerView.adapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                for (i in 0 until recyclerView.childCount) {
                    traverseViews(recyclerView.getChildAt(i))
                }
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = onChanged()
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = onChanged()
        })
    }

    private fun translateText(original: String, callback: (String) -> Unit) {
        // Use cache if available
        translationCache[original]?.let {
            callback(it)
            return
        }

        // Otherwise call viewModel
        viewModel.translate(original) { translated ->
            if (translated.isNotBlank()) {
                translationCache[original] = translated
                callback(translated)
            }
        }
    }

    fun translateFragmentViews(fragment: Fragment) {
        val root = fragment.view ?: return
        traverseViews(root)
    }

    private fun restoreViewTexts(view: View) {
        when (view) {
            is ViewGroup -> for (i in 0 until view.childCount) restoreViewTexts(view.getChildAt(i))
            is TextView -> {
                val tagKey = R.id.original_text
                val original = (view.getTag(tagKey) as? String)
                if (original != null) {
                    view.text = original
                }
            }
        }
    }

    fun restoreOriginals() {
        // restore toolbar
        originalToolbarTitle?.let {
            activity.runOnUiThread {
                (activity as? AppCompatActivity)?.supportActionBar?.title = it
            }
        }

        // restore bottom nav
        activity.findViewById<BottomNavigationView?>(R.id.nav_view)?.let { nav ->
            for ((id, original) in originalMenuTitles) {
                nav.menu.findItem(id)?.title = original
            }
        }

        // restore fragment content
        val root = activity.findViewById<View>(android.R.id.content)
        restoreViews(root)

        // clear cache after restoring
        translationCache.clear()
    }

    private fun restoreViews(view: View) {
        when (view) {
            is ViewGroup -> {
                for (i in 0 until view.childCount) restoreViews(view.getChildAt(i))
            }
            is TextView -> {
                val tagKey = R.id.original_text
                val original = view.getTag(tagKey) as? String
                if (original != null) {
                    view.text = original
                }
            }
        }
    }
}
