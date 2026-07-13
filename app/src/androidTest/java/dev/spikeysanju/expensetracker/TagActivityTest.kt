package dev.spikeysanju.expensetracker

import android.widget.AutoCompleteTextView
import androidx.recyclerview.widget.RecyclerView
import androidx.navigation.Navigation
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dev.spikeysanju.expensetracker.utils.DisableAnimationsRule
import dev.spikeysanju.expensetracker.utils.waitAndClick
import dev.spikeysanju.expensetracker.view.main.MainActivity
import org.hamcrest.Matchers.allOf

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

/**
 * Instrumented test for Tag management operations
 * Tests navigation to Tags screen and verifying default tags are displayed
 */
@LargeTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TagActivityTest {

    @get:Rule(order = 0)
    var disableAnimationsRule = DisableAnimationsRule()

    @get:Rule(order = 1)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    var activityScenarioRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun init() {
        hiltRule.inject()
        // Give the activity time to fully render
        Thread.sleep(1000)
    }

    private fun navigateToTagsScreen() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // Wait for dashboard to fully load
        onView(withId(R.id.addTransactionFragment))
            .check(matches(isDisplayed()))

        // Additional wait to ensure UI is stable
        Thread.sleep(200)

        // Open overflow menu using custom click to avoid coordinate issues
        try {
            // First try the standard approach
            onView(withContentDescription("More options"))
                .perform(waitAndClick())
        } catch (e: Exception) {
            // Fallback to openActionBarOverflowOrOptionsMenu if direct click fails
            openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getInstrumentation().targetContext)
        }

        // Wait for "Tags" to appear using UiAutomator
        device.waitForIdle(1_000)
        device.wait(Until.hasObject(By.text("Tags")), 2_000)
        device.findObject(By.text("Tags")).click()

        // Wait for navigation to complete
        device.waitForIdle(1_000)

        // Verify RecyclerView is displayed
        onView(withId(R.id.tagsRecyclerView))
            .check(matches(isDisplayed()))
    }

    private fun scrollToTag(tagName: String) {
        onView(withId(R.id.tagsRecyclerView))
            .perform(RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(tagName))))
    }

    private fun createTag(name: String, type: String) {
        // Click FAB to add new tag
        onView(withId(R.id.addTagFab)).perform(click())

        // Enter tag name
        onView(withId(R.id.tagNameEt))
            .perform(typeText(name), closeSoftKeyboard())

        // Select tag type
        onView(withId(R.id.tagTypeEt)).perform(waitAndClick())
        onView(withText(type))
            .inRoot(isPlatformPopup())
            .perform(click())

        // Confirm deletion
        onView(withId(android.R.id.button1)).perform(click())
    }

    @Test
    fun verifyDefaultTagsAreDisplayed() {
        navigateToTagsScreen()

        // Verify FAB is displayed
        onView(withId(R.id.addTagFab))
            .check(matches(isDisplayed()))

        // Verify default Expense tags are displayed
        onView(withText("Food"))
            .check(matches(isDisplayed()))

        onView(withText("Housing"))
            .check(matches(isDisplayed()))

        onView(withText("Transportation"))
            .check(matches(isDisplayed()))

        onView(withText("Healthcare"))
            .check(matches(isDisplayed()))

        // Verify default Income tags are displayed
        onView(withText("Work"))
            .check(matches(isDisplayed()))

    }

    private fun navigateDirectlyToTagsScreen() {
        activityScenarioRule.scenario.onActivity { activity ->
            Navigation.findNavController(activity, R.id.nav_host_fragment)
                .navigate(R.id.tagsFragment)
        }

        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(1_000)
        onView(withId(R.id.tagsRecyclerView)).check(matches(isDisplayed()))
    }

    @Test
    fun iconSearchKeepsAllTagFieldsReachable() {
        navigateDirectlyToTagsScreen()

        onView(withId(R.id.addTagFab)).perform(click())
        onView(withId(R.id.tagIconEt))
            .perform(scrollTo(), click(), replaceText("car"))

        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(1_000)
        onView(withId(R.id.tagIconEt))
            .check(matches(withText("car")))
            .check { view, _ ->
                val results = (view as AutoCompleteTextView).adapter
                assertTrue(results.count > 0)
                assertTrue(
                    (0 until results.count).all { index ->
                        results.getItem(index).toString().contains("car", ignoreCase = true)
                    }
                )
            }

        onView(withId(R.id.tagKeywordEt))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
    }

    @Test
    fun createExpenseTag() {
        navigateToTagsScreen()
        val tagName = "Test Expense Tag"
        createTag(tagName, "Expense")
        onView(withText(tagName)).check(matches(isDisplayed()))

        onView(allOf(withId(R.id.deleteTag), hasSibling(withText(tagName))))
            .perform(click())

        // Confirm deletion
        onView(withId(android.R.id.button1)).perform(click())
    }

    @Test
    fun createIncomeTag() {
        navigateToTagsScreen()
        val tagName = "Test Income Tag"
        createTag(tagName, "Income")
        onView(withText(tagName)).check(matches(isDisplayed()))

        onView(allOf(withId(R.id.deleteTag), hasSibling(withText(tagName))))
            .perform(click())

        // Confirm deletion
        onView(withId(android.R.id.button1)).perform(click())
    }

    @Test
    fun verifyEditTag() {
        navigateToTagsScreen()
        createTag("Tag To Edit", "Expense")

        // Find the edit button for "Tag To Edit" tag and click it
        onView(allOf(withId(R.id.editTag), hasSibling(withText("Tag To Edit"))))
            .perform(click())

        // Change tag name
        onView(withId(R.id.tagNameEt))
            .perform(clearText(), typeText("Tag Edited"), closeSoftKeyboard())

        // Click Save
        onView(withText("Save")).perform(click())

        // Verify updated tag is displayed
        onView(withText("Tag Edited")).check(matches(isDisplayed()))

        onView(allOf(withId(R.id.deleteTag), hasSibling(withText("Tag Edited"))))
            .perform(click())

        // Confirm deletion
        onView(withId(android.R.id.button1)).perform(click())

    }

    @Test
    fun verifyDeleteTag() {
        navigateToTagsScreen()
        createTag("Tag To Delete", "Expense")

        // Find the delete button for "Tag To Delete" tag and click it
        onView(allOf(withId(R.id.deleteTag), hasSibling(withText("Tag To Delete"))))
            .perform(click())

        // Confirm deletion
        onView(withId(android.R.id.button1)).perform(click())

        // Verify tag is removed
        onView(withText("Tag To Delete")).check(doesNotExist())
    }

    @Test
    fun verifyNewExpenseTagInAddTransaction() {
        val tagName = "New Expense Tag"
        navigateToTagsScreen()
        createTag(tagName, "Expense")

        // Go back to Dashboard
        pressBack()

        createTransaction("Test Expense Transaction", "100", "Expense", tagName)
    }

    @Test
    fun verifyNewIncomeTagInAddTransaction() {
        val tagName = "New Income Tag"
        navigateToTagsScreen()
        createTag(tagName, "Income")

        // Go back to Dashboard
        pressBack()

        createTransaction("Test Income Transaction", "100", "Income", tagName)

    }

    @Test
    fun verifyDeletedTagNotAvailableInAddTransaction() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val tagName = "Transportation"
        navigateToTagsScreen()

        // Find the delete button for the tag and click it
        onView(allOf(withId(R.id.deleteTag), hasSibling(withText(tagName))))
            .perform(click())

        // Confirm deletion
        onView(withId(android.R.id.button1)).perform(click())

        // Go back to Dashboard
        pressBack()

        // Open Add Transaction screen
        onView(withId(R.id.addTransactionFragment)).perform(click())

        device.waitForIdle(1_000)

        // Select Transaction Type "Expense" first
        onView(withId(R.id.et_transactionType)).perform(waitAndClick())
        onView(withText("Expense"))
            .inRoot(isPlatformPopup())
            .perform(click())

        device.waitForIdle(1_000)

        // Open Tag dropdown
        onView(withId(R.id.et_tag)).perform(waitAndClick())

        // Verify tag is NOT present
        onView(withText(tagName))
            .inRoot(isPlatformPopup())
            .check(doesNotExist())
    }

    @Test
    fun verifyCannotDeleteTagWithTransaction() {
        val tagName = "Food"
        createTransaction("Test Transaction", "100", "Expense", tagName)

        navigateToTagsScreen()

        // Try to delete
        onView(allOf(withId(R.id.deleteTag), hasSibling(withText(tagName))))
            .perform(click())

        // Confirm deletion
        onView(withId(android.R.id.button1)).perform(click())

        // Verify tag still exists (deletion failed)
        onView(withText(tagName)).check(matches(isDisplayed()))
    }

    private fun createTransaction(title: String, amount: String, type: String, tagName: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.waitForIdle(1_000)

        // Open Add Transaction screen
        onView(withId(R.id.addTransactionFragment)).perform(click())

        // Enter Title
        onView(withId(R.id.et_title)).perform(typeText(title), closeSoftKeyboard())

        // Enter Amount
        onView(withId(R.id.et_amount)).perform(typeText(amount), closeSoftKeyboard())

        // Select Transaction Type
        onView(withId(R.id.et_transactionType)).perform(waitAndClick())
        onView(withText(type))
            .inRoot(isPlatformPopup())
            .perform(click())

        // Select Tag
        onView(withId(R.id.et_tag)).perform(waitAndClick())
        onView(withText(tagName))
            .inRoot(isPlatformPopup())
            .perform(click())

        // Enter date
        onView(withId(R.id.et_when))
            .perform(replaceText("20/12/2024"), closeSoftKeyboard())

        // Enter note
        onView(withId(R.id.et_note))
            .perform(replaceText("Test note"), closeSoftKeyboard())

        // Click Save
        onView(withId(R.id.btn_save_transaction)).perform(click())
    }


}
