package com.aminpinjari.screentranslater

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
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
import com.aminpinjari.screentranslater.translate.TranslationManager
import com.aminpinjari.screentranslater.translate.TranslationViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val translationRepo by lazy { TranslationRepository(applicationContext) }
    private val translationVM: TranslationViewModel by viewModels {
        TranslationViewModel.Factory(translationRepo)
    }

    private lateinit var translationManager: TranslationManager

    private var loadingDialog: AlertDialog? = null
    private val loadingMessages = listOf(
        "Downloading language model…",
        "Almost there…",
        "Loading magic words…",
        "Preparing your translator…"
    )
    private var messageIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private var messageRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)

        translationManager = TranslationManager(this, translationVM, lifecycleScope)
        translationManager.startObserving(supportActionBar, binding.navView)

        binding.fabTranslate.setOnClickListener {
            LanguagePickerDialog.show(this) { lang, keep ->
                translationVM.setLanguage(lang)
                if (keep) {
                    translationVM.setPersistentLanguage(lang)
                } else {
                    translationVM.clearPersistentLanguage()
                }
            }
        }

        // Navigation listener → re-translate or restore depending on checkbox (persistentLanguage)
        navController.addOnDestinationChangedListener { _, _, _ ->
            val lang = translationVM.persistentLanguage.value
            if (lang != null) {
                translationManager.translateCurrentScreen(supportActionBar, binding.navView)
            } else {
                translationManager.restoreOriginals(supportActionBar, binding.navView)
            }
        }

        lifecycleScope.launch {
            translationVM.loading.collectLatest { isLoading ->
                if (isLoading) showLoadingDialog() else hideLoadingDialog()
            }
        }
    }

    private fun showLoadingDialog() {
        if (loadingDialog == null) {
            val progressBar = ProgressBar(this).apply { isIndeterminate = true }
            val progressText = TextView(this).apply {
                text = loadingMessages[0]
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 0)
                textSize = 16f
            }

            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(50, 50, 50, 50)
                addView(progressBar)
                addView(progressText)
            }

            loadingDialog = AlertDialog.Builder(this)
                .setTitle("Preparing Language")
                .setView(layout)
                .setCancelable(false)
                .create()

            messageRunnable = object : Runnable {
                override fun run() {
                    messageIndex = (messageIndex + 1) % loadingMessages.size
                    progressText.text = loadingMessages[messageIndex]
                    handler.postDelayed(this, 2000)
                }
            }
        }
        loadingDialog?.show()
        messageRunnable?.let { handler.postDelayed(it, 2000) }
    }

    private fun hideLoadingDialog() {
        messageRunnable?.let { handler.removeCallbacks(it) }
        messageRunnable = null
        loadingDialog?.dismiss()
        loadingDialog = null
    }
}
