package com.aminpinjari.screentranslater

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.aminpinjari.screentranslater.databinding.ActivityMainBinding
import com.aminpinjari.screentranslater.repo.TranslationRepository
import com.aminpinjari.screentranslater.translate.LanguagePickerDialog
import com.aminpinjari.screentranslater.translate.TranslationViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val translationRepository by lazy { TranslationRepository(applicationContext) }
    private val translationVM: TranslationViewModel by viewModels {
        TranslationViewModel.Factory(translationRepository)
    }

    private var loadingDialog: AlertDialog? = null

    // store original titles for BottomNavigation menu items (menuItemId -> originalTitle)
    private val originalMenuTitles = mutableMapOf<Int, String>()

    // store original toolbar title
    private var originalToolbarTitle: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // toolbar
        setSupportActionBar(binding.toolbar)

        // navigation
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)

        // FAB opens language picker
        binding.fabTranslate.setOnClickListener {
            LanguagePickerDialog.show(this) { lang ->
                translationVM.setLanguage(lang)
            }
        }

        // show/hide loader while model downloading
        lifecycleScope.launch {
            translationVM.loading.collectLatest { isLoading ->
                if (isLoading) showLoadingDialog() else hideLoadingDialog()
            }
        }

        // when translation trigger emits (translator ready or immediate), run translation
        lifecycleScope.launch {
            translationVM.translationTrigger.collectLatest { lang ->
                // translate all visible UI using stored originals
                translateAllScreens()
            }
        }
    }

    private fun translateAllScreens() {
        // translate all TextViews in the activity root
        traverseViews(findViewById(android.R.id.content))

        // translate toolbar title
        translateToolbarTitle()

        // translate bottom navigation menu titles
        translateBottomNavMenu()

        // translate visible fragment's views (NavHost)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main)
        navHostFragment?.childFragmentManager?.fragments?.forEach { frag ->
            frag.view?.let { traverseViews(it) }
        }
    }

    private fun translateToolbarTitle() {
        val actionBar = supportActionBar ?: return
        val current = actionBar.title?.toString() ?: return
        // store original once
        if (originalToolbarTitle == null) originalToolbarTitle = current

        val originalToTranslate = originalToolbarTitle ?: current
        translationVM.translate(originalToTranslate) { translated ->
            runOnUiThread { actionBar.title = translated }
        }
    }

    private fun translateBottomNavMenu() {
        val menu = binding.navView.menu
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val id = item.itemId
            val original = originalMenuTitles[id] ?: item.title.toString().also {
                originalMenuTitles[id] = it
            }
            translationVM.translate(original) { translated ->
                runOnUiThread { item.title = translated }
            }
        }
    }

    private fun traverseViews(view: View) {
        when (view) {
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    traverseViews(view.getChildAt(i))
                }
            }
            is TextView -> {
                // use stored original if present; if not, store current as original
                val tagKey = com.aminpinjari.screentranslater.R.id.original_text
                val original = (view.getTag(tagKey) as? String) ?: view.text.toString().also {
                    view.setTag(tagKey, it)
                }

                translationVM.translate(original) { translated ->
                    view.post { view.text = translated }
                }
            }
            else -> {
                // nothing
            }
        }
    }

    private fun showLoadingDialog() {
        if (loadingDialog == null) {
            loadingDialog = AlertDialog.Builder(this)
                .setView(ProgressBar(this))
                .setCancelable(false)
                .create()
        }
        loadingDialog?.show()
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }
}
