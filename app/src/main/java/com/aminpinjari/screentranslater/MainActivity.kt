package com.aminpinjari.screentranslater

import android.os.Bundle
import androidx.activity.viewModels
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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val translationRepo by lazy { TranslationRepository(applicationContext) }
    private val translationVM: TranslationViewModel by viewModels {
        TranslationViewModel.Factory(translationRepo)
    }

    private lateinit var translationManager: TranslationManager

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

        // initialize TranslationManager
        translationManager = TranslationManager(
            this,
            translationVM,
            lifecycleScope
        )
        translationManager.startObserving(supportActionBar, binding.navView)

        // FAB for language selection
        binding.fabTranslate.setOnClickListener {
            LanguagePickerDialog.show(this) { lang ->
                translationVM.setLanguage(lang)
            }
        }
    }
}
