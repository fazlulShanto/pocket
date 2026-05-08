package dev.spikeysanju.expensetracker

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dev.spikeysanju.expensetracker.utils.DisableAnimationsRule
import dev.spikeysanju.expensetracker.utils.waitAndClick
import dev.spikeysanju.expensetracker.view.main.MainActivity
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for basic transaction operations
 * Tests creating income/expense transactions and verifying they appear on dashboard
 */
@LargeTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

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

    @Test
    fun createAndVerifyIncomeTransaction() {
        // Wait for the dashboard to load and click the add button
        onView(withId(R.id.addTransactionFragment))
            .check(matches(isDisplayed()))
            .perform(waitAndClick())

        // Wait for navigation by checking for the save button to appear
        onView(withId(R.id.btn_save_transaction))
            .check(matches(isDisplayed()))

        // Fill in the transaction details for income
        onView(withId(R.id.et_title))
            .perform(replaceText("Salary"), closeSoftKeyboard())

        onView(withId(R.id.et_amount))
            .perform(replaceText("5000"), closeSoftKeyboard())

        onView(withId(R.id.et_transactionType))
            .perform(replaceText("Income"), closeSoftKeyboard())

        onView(withId(R.id.et_tag))
            .perform(replaceText("Personal"), closeSoftKeyboard())

        onView(withId(R.id.et_when))
            .perform(replaceText("20/12/2024"), closeSoftKeyboard())

        onView(withId(R.id.et_note))
            .perform(replaceText("Monthly salary payment"), closeSoftKeyboard())

        // Click save button
        onView(withId(R.id.btn_save_transaction))
            .perform(waitAndClick())

        // Verify transaction appears in RecyclerView
        onView(withId(R.id.transaction_rv))
            .check(matches(isDisplayed()))

        // Verify transaction with title appears in the list
        onView(withText("Salary"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun createAndVerifyExpenseTransaction() {
        // Wait for the dashboard to load and click the add button
        onView(withId(R.id.addTransactionFragment))
            .check(matches(isDisplayed()))
            .perform(waitAndClick())

        // Wait for navigation by checking for the save button to appear
        onView(withId(R.id.btn_save_transaction))
            .check(matches(isDisplayed()))

        // Fill in the transaction details for expense
        onView(withId(R.id.et_title))
            .perform(replaceText("Groceries"), closeSoftKeyboard())

        onView(withId(R.id.et_amount))
            .perform(replaceText("150"), closeSoftKeyboard())

        onView(withId(R.id.et_transactionType))
            .perform(replaceText("Expense"), closeSoftKeyboard())

        onView(withId(R.id.et_tag))
            .perform(replaceText("Food"), closeSoftKeyboard())

        onView(withId(R.id.et_when))
            .perform(replaceText("20/12/2024"), closeSoftKeyboard())

        onView(withId(R.id.et_note))
            .perform(replaceText("Weekly grocery shopping"), closeSoftKeyboard())

        // Click save button
        onView(withId(R.id.btn_save_transaction))
            .perform(waitAndClick())

        // Verify transaction appears in RecyclerView (not empty state)
        onView(withId(R.id.transaction_rv))
            .check(matches(isDisplayed()))

        // Verify transaction with title appears in the list
        onView(withText("Groceries"))
            .check(matches(isDisplayed()))
    }

}
