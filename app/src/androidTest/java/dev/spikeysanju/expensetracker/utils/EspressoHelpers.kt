package dev.spikeysanju.expensetracker.utils

import android.view.View
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import org.hamcrest.Matcher

/**
 * Custom ViewAction that waits for the view to be displayed before clicking
 */
fun waitAndClick(timeout: Long = 3000): ViewAction {
    return object : ViewAction {
        override fun getConstraints(): Matcher<View> {
            return isDisplayed()
        }

        override fun getDescription(): String {
            return "Wait for view to be displayed and then click"
        }

        override fun perform(uiController: UiController, view: View) {
            val endTime = System.currentTimeMillis() + timeout

            // Wait for view to be fully displayed and stable
            do {
                uiController.loopMainThreadForAtLeast(100)
            } while (System.currentTimeMillis() < endTime && !view.isShown)

            // Extra delay to ensure view is ready for interaction
            uiController.loopMainThreadForAtLeast(300)

            // Perform the actual click
            view.performClick()
        }
    }
}
