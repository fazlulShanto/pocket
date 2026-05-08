package dev.spikeysanju.expensetracker.utils

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * JUnit rule that disables device animations before tests and re-enables them after.
 * This prevents Espresso flakiness caused by animations interfering with UI interactions.
 */
class DisableAnimationsRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

                // Disable animations
                disableAnimations(device)

                // Give system time to apply settings
                Thread.sleep(500)

                try {
                    base.evaluate()
                } finally {
                    // Re-enable animations after test
                    enableAnimations(device)
                }
            }
        }
    }

    private fun disableAnimations(device: UiDevice) {
        Log.d("DisableAnimationsRule", "Disabling animations")
        device.executeShellCommand("settings put global window_animation_scale 0")
        device.executeShellCommand("settings put global transition_animation_scale 0")
        device.executeShellCommand("settings put global animator_duration_scale 0")

        // Verify settings were applied
        val windowScale = device.executeShellCommand("settings get global window_animation_scale")
        val transitionScale = device.executeShellCommand("settings get global transition_animation_scale")
        val animatorScale = device.executeShellCommand("settings get global animator_duration_scale")

        Log.d("DisableAnimationsRule", "Window scale: $windowScale")
        Log.d("DisableAnimationsRule", "Transition scale: $transitionScale")
        Log.d("DisableAnimationsRule", "Animator scale: $animatorScale")
    }

    private fun enableAnimations(device: UiDevice) {
        Log.d("DisableAnimationsRule", "Re-enabling animations")
        device.executeShellCommand("settings put global window_animation_scale 1")
        device.executeShellCommand("settings put global transition_animation_scale 1")
        device.executeShellCommand("settings put global animator_duration_scale 1")
    }
}
