package dev.spikeysanju.expensetracker.utils

import android.content.res.Resources
import androidx.annotation.DrawableRes
import dev.spikeysanju.expensetracker.R
import dev.spikeysanju.expensetracker.app.ExpenseTracker
import dev.spikeysanju.expensetracker.model.Tag
import java.util.Locale

data class TagIconOption(
    val key: String,
    val label: String,
    @DrawableRes val drawableRes: Int
)

data class TagSeedDefinition(
    val tagName: String,
    val tagType: String,
    val iconName: String,
    val keyword: String = ""
)

object TagCatalog {
    private val builtInIconOptions = listOf(
        TagIconOption(key = "food", label = "Food", drawableRes = R.drawable.ic_food),
        TagIconOption(key = "housing", label = "Housing", drawableRes = R.drawable.ic_housing),
        TagIconOption(
            key = "transport",
            label = "Transportation",
            drawableRes = R.drawable.ic_transport
        ),
        TagIconOption(key = "health", label = "Healthcare", drawableRes = R.drawable.ic_medical),
        TagIconOption(key = "income", label = "Income", drawableRes = R.drawable.ic_income),
        TagIconOption(
            key = "personal",
            label = "Personal",
            drawableRes = R.drawable.ic_personal_spending
        ),
        TagIconOption(
            key = "entertainment",
            label = "Entertainment",
            drawableRes = R.drawable.ic_entertainment
        ),
        TagIconOption(key = "savings", label = "Savings", drawableRes = R.drawable.ic_savings),
        TagIconOption(key = Tag.DEFAULT_ICON_NAME, label = "Other", drawableRes = R.drawable.ic_others)
    )

    val iconOptions: List<TagIconOption> by lazy {
        builtInIconOptions + loadLucideIconOptions()
    }

    val defaultSeedTags = listOf(
        TagSeedDefinition(
            tagName = "Food",
            tagType = "Expense",
            iconName = "food",
            keyword = "food, chicken, lunch, dinner, breakfast, groceries, snacks, cafe, restaurant"
        ),
        TagSeedDefinition(
            tagName = "Housing",
            tagType = "Expense",
            iconName = "housing",
            keyword = "rent, housing, apartment, mortgage, house"
        ),
        TagSeedDefinition(
            tagName = "Transportation",
            tagType = "Expense",
            iconName = "transport",
            keyword = "transport, bus, taxi, uber, fuel, train"
        ),
        TagSeedDefinition(
            tagName = "Healthcare",
            tagType = "Expense",
            iconName = "health",
            keyword = "doctor, medicine, pharmacy, clinic, hospital"
        ),
        TagSeedDefinition(
            tagName = "Personal",
            tagType = "Expense",
            iconName = "personal",
            keyword = "shopping, clothes, salon, personal"
        ),
        TagSeedDefinition(
            tagName = "Work",
            tagType = "Income",
            iconName = "income",
            keyword = "salary, paycheck, payroll, freelance, invoice"
        ),
        TagSeedDefinition(
            tagName = "Personal",
            tagType = "Income",
            iconName = "income",
            keyword = "gift, cashback, refund, bonus"
        )
    )

    private val iconOptionsByKey: Map<String, TagIconOption> by lazy {
        iconOptions.associateBy { it.key }
    }

    private val iconKeysByLabel: Map<String, String> by lazy {
        val labelsToKeys = mutableMapOf<String, String>()
        iconOptions.forEach { option ->
            labelsToKeys.putIfAbsent(option.label.lowercase(Locale.ROOT), option.key)
        }
        labelsToKeys
    }

    fun resolveIconRes(iconName: String?): Int {
        val normalizedKey = normalizeIconKey(iconName)
        return iconOptionsByKey[normalizedKey]?.drawableRes ?: R.drawable.ic_others
    }

    fun resolveIconLabel(iconName: String?): String {
        val normalizedKey = normalizeIconKey(iconName)
        return iconOptionsByKey[normalizedKey]?.label ?: iconOptions.last().label
    }

    fun iconKeyFromLabel(label: String?): String {
        val normalizedLabel = label?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return iconKeysByLabel[normalizedLabel] ?: Tag.DEFAULT_ICON_NAME
    }

    fun normalizeIconKey(iconName: String?): String {
        val normalizedKey = iconName?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return iconOptionsByKey[normalizedKey]?.key ?: Tag.DEFAULT_ICON_NAME
    }

    fun defaultIconNameForTag(tagName: String, tagType: String? = null): String {
        return defaultSeedTags.firstOrNull { seed ->
            seed.tagName.equals(tagName, ignoreCase = true) &&
                (tagType == null || seed.tagType.equals(tagType, ignoreCase = true))
        }?.iconName ?: Tag.DEFAULT_ICON_NAME
    }

    fun defaultKeywordForTag(tagName: String, tagType: String): String {
        return defaultSeedTags.firstOrNull { seed ->
            seed.tagName.equals(tagName, ignoreCase = true) &&
                seed.tagType.equals(tagType, ignoreCase = true)
        }?.keyword.orEmpty()
    }

    private fun loadLucideIconOptions(): List<TagIconOption> {
        val resources = appResourcesOrNull() ?: return emptyList()
        val typedArray = resources.obtainTypedArray(R.array.tag_lucide_icon_refs)

        return try {
            val options = mutableListOf<TagIconOption>()
            for (index in 0 until typedArray.length()) {
                val drawableRes = typedArray.getResourceId(index, 0)
                if (drawableRes == 0) continue

                val key = resources.getResourceEntryName(drawableRes)
                options += TagIconOption(
                    key = key,
                    label = formatLucideLabel(key),
                    drawableRes = drawableRes
                )
            }
            options
        } finally {
            typedArray.recycle()
        }
    }

    private fun appResourcesOrNull(): Resources? {
        return runCatching { ExpenseTracker.appContext.resources }.getOrNull()
    }

    private fun formatLucideLabel(resourceName: String): String {
        return resourceName
            .split('_')
            .joinToString(" ") { segment ->
                when {
                    segment.isBlank() -> segment
                    segment.all(Char::isDigit) -> segment
                    segment.length == 1 -> segment.uppercase(Locale.ROOT)
                    segment.first().isDigit() -> segment.uppercase(Locale.ROOT)
                    else -> segment.replaceFirstChar { character ->
                        if (character.isLowerCase()) {
                            character.titlecase(Locale.ROOT)
                        } else {
                            character.toString()
                        }
                    }
                }
            }
    }
}