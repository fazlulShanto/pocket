package dev.spikeysanju.expensetracker.services.exportcsv

import dev.spikeysanju.expensetracker.model.Transaction

val TRANSACTION_CSV_HEADER = arrayOf(
    "title",
    "amount",
    "transactionType",
    "tag",
    "date",
    "note",
    "createdAt"
)

fun List<Transaction>.toCsvRows() = map {
    arrayOf(
        it.title,
        it.amount.toString(),
        it.transactionType,
        it.tag,
        it.date,
        it.note,
        it.createdAt.toString()
    )
}
