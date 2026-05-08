package dev.spikeysanju.expensetracker.view.main

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.MenuItemCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.navigation.ui.setupActionBarWithNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.spikeysanju.expensetracker.R
import dev.spikeysanju.expensetracker.data.local.datastore.UIModeImpl
import dev.spikeysanju.expensetracker.databinding.ActivityMainBinding
import dev.spikeysanju.expensetracker.repo.TransactionRepo
import dev.spikeysanju.expensetracker.services.exportcsv.ExportCsvService
import dev.spikeysanju.expensetracker.view.main.viewmodel.TransactionViewModel
import kotlinx.coroutines.flow.collect
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var navHostFragment: NavHostFragment
    private lateinit var appBarConfiguration: AppBarConfiguration
    private val topLevelDestinations = setOf(
        R.id.dashboardFragment,
        R.id.transactionsFragment,
        R.id.reportingFragment,
        R.id.settingsFragment
    )

    @Inject
    lateinit var repo: TransactionRepo

    @Inject
    lateinit var exportCsvService: ExportCsvService

    @Inject
    lateinit var themeManager: UIModeImpl
    private val viewModel: TransactionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /**
         * Just so the viewModel doesn't get removed by the compiler, as it isn't used
         * anywhere here for now
         */
        viewModel

        initViews(binding)
        observeThemeMode()
        observeNavElements(binding, navHostFragment.navController)
    }

    private fun observeThemeMode() {
        lifecycleScope.launchWhenStarted {
            viewModel.getUIMode.collect {
                val mode = when (it) {
                    true -> AppCompatDelegate.MODE_NIGHT_YES
                    false -> AppCompatDelegate.MODE_NIGHT_NO
                }
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }
    }

    private fun observeNavElements(
        binding: ActivityMainBinding,
        navController: NavController
    ) {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isTopLevelDestination = destination.id in topLevelDestinations
            binding.appbar.visibility = if (isTopLevelDestination) View.GONE else View.VISIBLE
            binding.bottomNavigation.visibility =
                if (isTopLevelDestination) View.VISIBLE else View.GONE

            supportActionBar?.setDisplayShowTitleEnabled(!isTopLevelDestination)

            if (destination.id == R.id.addTransactionFragment) {
                binding.toolbar.title = getString(R.string.text_add_transaction)
            }
        }
    }

    private fun initViews(binding: ActivityMainBinding) {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment?
            ?: return

        with(navHostFragment.navController) {
            appBarConfiguration = AppBarConfiguration(topLevelDestinations)
            setupActionBarWithNavController(this, appBarConfiguration)
            binding.bottomNavigation.setupWithNavController(this)
            binding.bottomNavigation.itemIconTintList = null
            binding.bottomNavigation.setOnItemReselectedListener {
                // Ignore reselection for top-level destinations.
            }
        }

        MenuItemCompat.setContentDescription(
            binding.bottomNavigation.menu.findItem(R.id.addTransactionFragment),
            getString(R.string.text_add_transaction)
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        return navHostFragment.navController.navigateUp() || super.onSupportNavigateUp()
    }
}
