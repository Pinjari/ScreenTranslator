package com.aminpinjari.screentranslater.translate

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
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
import java.lang.ref.WeakReference
import java.util.LinkedHashMap
import java.util.WeakHashMap

/**
 * TranslationManager - fixed and optimized
 *
 * Key fixes:
 *  - Preserve original text stored in R.id.original_text; never overwrite it with translated text.
 *  - Only update the original when the view was rebound with a new original (and not when it currently shows a translation).
 *  - Avoid skipping translations erroneously on subsequent language switches.
 *
 * Performance & safety:
 *  - Per-language bounded LRU cache.
 *  - WeakHashMaps for view/listener bookkeeping to avoid leaks.
 *  - Throttled RecyclerView & GlobalLayout handling.
 *  - Minimal UI updates (only set text when different).
 *
 * Public API expected by MainActivity remains the same:
 *  - startObserving(actionBar, bottomNav)
 *  - translateCurrentScreen(actionBar, bottomNav)
 *  - restoreOriginals(actionBar, bottomNav)
 *  - translateFragmentViews(fragment)
 */
class TranslationManager(
    private val activity: AppCompatActivity,
    private val viewModel: TranslationViewModel,
    private val lifecycleScope: LifecycleCoroutineScope
) {

    private val TAG = "TranslationManager"
    private val DEBUG = false // set true to enable debug logs

    // Keep originals for toolbar and bottom nav menu items
    private val originalMenuTitles = mutableMapOf<Int, String>()
    private var originalToolbarTitle: String? = null

    // Per-language caches (LRU per language)
    private fun createBoundedCache(maxSize: Int = 500): MutableMap<String, String> {
        return object : LinkedHashMap<String, String>(maxSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean {
                return size > maxSize
            }
        }
    }

    // language -> (original -> translated)
    // LinkedHashMap used so we can remove oldest languages if needed
    private val translationCache = LinkedHashMap<String, MutableMap<String, String>>(8, 0.75f, true)
    private val maxLanguagesToCache = 2
    private var currentLang: String? = null

    // Remember last translated text per TextView (weak reference map)
    // Helps avoid redundant writes/flicker
    private val translatedViews = WeakHashMap<TextView, String>()

    // Observers bookkeeping
    private val observedRecyclerViews = WeakHashMap<RecyclerView, Boolean>()
    private val observedRootListeners = WeakHashMap<View, ViewTreeObserver.OnGlobalLayoutListener>()

    // Debounce handler for layout events
    private val handler = Handler(Looper.getMainLooper())
    private val layoutDebounceMs = 200L

    // Avoid re-translating the same fragment repeatedly
    private var lastTranslatedFragment: WeakReference<Fragment>? = null

    // ---------- Public API ----------

    /**
     * Start observing translationTrigger from the view model.
     * When a new language is emitted we run translations.
     */
    fun startObserving(actionBar: ActionBar?, bottomNav: BottomNavigationView?) {
        lifecycleScope.launch {
            viewModel.translationTrigger.collectLatest { lang ->
                currentLang = lang
                if (DEBUG) Log.d(TAG, "Language changed -> $lang")

                // keep only a few language caches to bound memory
                maintainCacheLimit(lang)

                // ensure layout is stable then translate
                activity.findViewById<View>(android.R.id.content)?.post {
                    translateAll(actionBar, bottomNav, forceUpdate = true)
                    translateFragments(forceUpdate = true)
                }
            }
        }
    }

    /**
     * Translate the current screen explicitly.
     */
    fun translateCurrentScreen(actionBar: ActionBar?, bottomNav: BottomNavigationView?) {
        activity.findViewById<View>(android.R.id.content)?.post {
            translateAll(actionBar, bottomNav, forceUpdate = true)
            translateFragments(forceUpdate = true)
        }
    }

    /**
     * Restore original texts and remove listeners.
     */
    fun restoreOriginals(actionBar: ActionBar?, bottomNav: BottomNavigationView?) {
        actionBar?.title = originalToolbarTitle ?: actionBar?.title

        bottomNav?.menu?.let { menu ->
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                originalMenuTitles[item.itemId]?.let { item.title = it }
            }
        }

        // restore fragment content
        val navHostFragment =
            activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main)
        navHostFragment?.childFragmentManager?.fragments?.forEach { frag ->
            frag.view?.let { restoreViewTexts(it) }
        }

        // cleanup global layout listeners
        val it = observedRootListeners.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            val root = entry.key
            val listener = entry.value
            try {
                if (root.viewTreeObserver.isAlive) {
                    root.viewTreeObserver.removeOnGlobalLayoutListener(listener)
                }
            } catch (_: Exception) {
            }
            it.remove()
        }
    }

    // ---------- Internal translation flow ----------

    private fun translateAll(actionBar: ActionBar?, bottomNav: BottomNavigationView?, forceUpdate: Boolean) {
        traverseViews(activity.findViewById(android.R.id.content), forceUpdate)
        actionBar?.let { translateToolbar(it, forceUpdate) }
        bottomNav?.let { translateBottomNav(it, forceUpdate) }
    }

    private fun translateFragments(forceUpdate: Boolean) {
        val navHostFragment =
            activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main)

        navHostFragment?.childFragmentManager?.fragments?.forEach { fragment ->
            // skip redundant work for same fragment unless forced
            val frag = fragment
            val fragId = frag.id
            if (forceUpdate || lastTranslatedFragment?.get()?.id != fragId) {
                fragment.view?.let {
                    // ensure dynamic children get observed
                    ensureObserveRoot(it)
                    traverseViews(it, forceUpdate)
                }
                lastTranslatedFragment = WeakReference(fragment)
            }
        }
    }

    private fun translateToolbar(actionBar: ActionBar, forceUpdate: Boolean) {
        val cur = actionBar.title?.toString() ?: return
        if (originalToolbarTitle == null) originalToolbarTitle = cur
        val original = originalToolbarTitle ?: cur

        translateText(original, forceUpdate) { translated ->
            if (activity.isDestroyed || activity.isFinishing) return@translateText
            if (translated.isNotBlank() && actionBar.title != translated) {
                activity.runOnUiThread { actionBar.title = translated }
            }
        }
    }

    private fun translateBottomNav(bottomNav: BottomNavigationView, forceUpdate: Boolean) {
        for (i in 0 until bottomNav.menu.size()) {
            val item = bottomNav.menu.getItem(i)
            val id = item.itemId
            val original = originalMenuTitles[id] ?: item.title.toString().also {
                originalMenuTitles[id] = it
            }
            translateText(original, forceUpdate) { translated ->
                if (activity.isDestroyed || activity.isFinishing) return@translateText
                if (translated.isNotBlank() && item.title != translated) {
                    activity.runOnUiThread { item.title = translated }
                }
            }
        }
    }

    /**
     * Recursively traverse view tree. For TextViews we handle translations.
     */
    private fun traverseViews(view: View?, forceUpdate: Boolean) {
        if (view == null) return
        when (view) {
            is ViewGroup -> {
                if (view is RecyclerView) translateRecyclerView(view, forceUpdate)
                for (i in 0 until view.childCount) {
                    traverseViews(view.getChildAt(i), forceUpdate)
                }
            }
            is TextView -> handleTextView(view, forceUpdate)
            else -> {
                // ignore other types
            }
        }
    }

    /**
     * Handle a single TextView; key points:
     * - store original in R.id.original_text on first-seen (or on genuine rebind)
     * - DO NOT overwrite original if the text is currently a translation
     * - decide whether translation needed using lastTranslatedLang tag
     * - use cache where possible and only update UI if different
     */
    private fun handleTextView(tv: TextView, forceUpdate: Boolean) {
        val origKey = R.id.original_text
        val langKey = R.id.translated_tag

        val currentText = tv.text?.toString() ?: ""
        val storedOriginal = tv.getTag(origKey) as? String
        val lastTranslatedLang = tv.getTag(langKey) as? String
        val lastTranslatedText = translatedViews[tv]

        // 1) establish storedOriginal safely:
        if (storedOriginal == null) {
            // first time we see this view -> store current text as original
            tv.setTag(origKey, currentText)
            tv.setTag(langKey, null)
            if (DEBUG) Log.d(TAG, "Stored original (first time): $currentText")
        } else if (storedOriginal != currentText) {
            // text differs from stored original. Could be:
            //  A) view was rebound with a NEW original (adapter bind) -> lastTranslatedLang == null typically
            //  B) view currently shows a translated string (lastTranslatedLang != null)
            // We must update storedOriginal only in case A (a real rebind with new original).
            if (lastTranslatedLang == null) {
                // genuine rebind: update original
                tv.setTag(origKey, currentText)
                tv.setTag(langKey, null)
                if (DEBUG) Log.d(TAG, "View rebound, updated original to: $currentText")
            } else {
                // It's a translated text (previous language). DO NOT overwrite the stored original.
                if (DEBUG) Log.d(TAG, "View shows previous translation; keep stored original.")
            }
        }

        val originalToTranslate = tv.getTag(origKey) as? String ?: currentText

        // Skip trivial strings (blank or numeric-only)
        if (originalToTranslate.isBlank() || originalToTranslate.all { it.isDigit() }) return

        // If already translated to current language and not forceUpdate -> skip
        if (!forceUpdate && lastTranslatedLang == currentLang) {
            if (DEBUG) Log.d(TAG, "Skipping; view already translated to currentLang: $currentLang")
            return
        }

        // If we have a cached translation and it's equal to current visible text, just mark tag and skip writing
        val lang = currentLang
        if (lang == null) {
            // no language selected — nothing to do
            return
        }

        val langCache = getOrCreateLangCache(lang)
        val cached = langCache[originalToTranslate]
        if (!forceUpdate && cached != null) {
            // apply only if different
            if (tv.text.toString() != cached) {
                if (activity.isDestroyed || activity.isFinishing) return
                activity.runOnUiThread {
                    tv.text = cached
                    tv.setTag(langKey, lang)
                    translatedViews[tv] = cached
                }
            } else {
                // same as shown, just mark translated tag
                tv.setTag(langKey, lang)
                translatedViews[tv] = cached
            }
            if (DEBUG) Log.d(TAG, "Served from cache for '$originalToTranslate' -> '$cached'")
            return
        }

        // Not cached or forceUpdate -> translate via viewModel
        viewModel.translate(originalToTranslate) { translated ->
            if (activity.isDestroyed || activity.isFinishing) return@translate
            if (translated.isNotBlank()) {
                // store in cache
                langCache[originalToTranslate] = translated

                // apply only if changed from current text (avoid layout churn)
                if (tv.text.toString() != translated) {
                    activity.runOnUiThread {
                        tv.text = translated
                        tv.setTag(langKey, lang)
                        translatedViews[tv] = translated
                    }
                } else {
                    // just mark tag
                    tv.setTag(langKey, lang)
                    translatedViews[tv] = translated
                }
                if (DEBUG) Log.d(TAG, "Translated '$originalToTranslate' -> '$translated'")
            }
        }
    }

    private fun translateRecyclerView(recyclerView: RecyclerView, forceUpdate: Boolean) {
        // translate currently visible children
        for (i in 0 until recyclerView.childCount) {
            traverseViews(recyclerView.getChildAt(i), forceUpdate)
        }

        if (observedRecyclerViews.containsKey(recyclerView)) return
        observedRecyclerViews[recyclerView] = true

        // throttled scroll listener
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var lastTs = 0L
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val now = System.currentTimeMillis()
                if (now - lastTs > 120L) {
                    lastTs = now
                    for (i in 0 until rv.childCount) {
                        traverseViews(rv.getChildAt(i), forceUpdate)
                    }
                }
            }
        })

        // adapter observer -> translate visible children on changes
        recyclerView.adapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                for (i in 0 until recyclerView.childCount) {
                    traverseViews(recyclerView.getChildAt(i), forceUpdate)
                }
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = onChanged()
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = onChanged()
        })
    }

    /**
     * Translate a single string (internal helper). Uses per-language cache.
     * NOTE: viewModel.translate is asynchronous and must be safe to call frequently.
     */
    private fun translateText(original: String, forceUpdate: Boolean, callback: (String) -> Unit) {
        val lang = currentLang ?: run {
            callback(original)
            return
        }

        val langCache = getOrCreateLangCache(lang)
        if (!forceUpdate && langCache.containsKey(original)) {
            callback(langCache[original] ?: original)
            return
        }

        viewModel.translate(original) { translated ->
            if (translated.isNotBlank()) langCache[original] = translated
            callback(translated)
        }
    }

    /**
     * Translate one fragment's view tree and ensure root is observed for dynamic children.
     */
    fun translateFragmentViews(fragment: Fragment, forceUpdate: Boolean = false) {
        val root = fragment.view ?: return
        ensureObserveRoot(root)
        root.post { traverseViews(root, forceUpdate) }
    }

    /**
     * Restore view texts to their original saved values (R.id.original_text).
     */
    private fun restoreViewTexts(view: View) {
        when (view) {
            is ViewGroup -> for (i in 0 until view.childCount) restoreViewTexts(view.getChildAt(i))
            is TextView -> {
                val origKey = R.id.original_text
                val original = view.getTag(origKey) as? String
                if (original != null) view.text = original
                view.setTag(R.id.translated_tag, null)
                translatedViews.remove(view)
            }
        }
    }

    /**
     * Observe root view's global layout so dynamically added children are discovered.
     * Debounced to avoid thrashing.
     */
    private fun ensureObserveRoot(root: View) {
        if (observedRootListeners.containsKey(root)) return

        val runnable = Runnable { traverseViews(root, forceUpdate = false) }
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            handler.removeCallbacks(runnable)
            handler.postDelayed(runnable, layoutDebounceMs)
        }

        try {
            root.viewTreeObserver.addOnGlobalLayoutListener(listener)
            observedRootListeners[root] = listener
        } catch (ex: Exception) {
            if (DEBUG) Log.w(TAG, "Failed to add global layout listener: ${ex.message}")
        }
    }

    // ---------- Cache helpers ----------

    private fun getOrCreateLangCache(lang: String): MutableMap<String, String> {
        translationCache[lang]?.let {
            // touch to update order
            translationCache.remove(lang)
            translationCache[lang] = it
            return it
        }
        // create and insert
        val newMap = createBoundedCache()
        translationCache[lang] = newMap
        maintainCacheLimit(lang)
        return newMap
    }

    /**
     * Keep only most recent X language caches to bound memory usage.
     */
    private fun maintainCacheLimit(newLang: String) {
        if (translationCache.containsKey(newLang) && translationCache.size <= maxLanguagesToCache) return
        while (translationCache.size > maxLanguagesToCache) {
            val eldestKey = translationCache.entries.iterator().next().key
            translationCache.remove(eldestKey)
            if (DEBUG) Log.d(TAG, "Evicted language cache: $eldestKey")
        }
    }
}
