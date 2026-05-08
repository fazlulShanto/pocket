package dev.spikeysanju.expensetracker.utils

enum class SupportedCurrency(
    val code: String,
    val displayName: String,
    val symbol: String
) {
    USD(code = "USD", displayName = "USD", symbol = "$"),
    GBP(code = "GBP", displayName = "Pound", symbol = "£"),
    EUR(code = "EUR", displayName = "Euro", symbol = "€"),
    CNY(code = "CNY", displayName = "Chinese", symbol = "¥"),
    BDT(code = "BDT", displayName = "BDT", symbol = "৳");

    companion object {
        val DEFAULT = BDT

        fun fromCode(code: String?): SupportedCurrency {
            return values().firstOrNull { currency ->
                currency.code.equals(code, ignoreCase = true)
            } ?: DEFAULT
        }
    }
}