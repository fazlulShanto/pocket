package dev.spikeysanju.expensetracker.app

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ExpenseTracker : Application() {

	override fun onCreate() {
		super.onCreate()
		appContext = applicationContext
	}

	companion object {
		lateinit var appContext: Context
			private set
	}
}
