package dev.spikeysanju.expensetracker.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "tags")
data class Tag(
    @ColumnInfo(name = "tagName")
    var tagName: String,
    @ColumnInfo(name = "tagType")
    var tagType: String, // "Income" or "Expense"
    @ColumnInfo(name = "iconName", defaultValue = DEFAULT_ICON_NAME)
    var iconName: String = DEFAULT_ICON_NAME,
    @ColumnInfo(name = "keyword", defaultValue = "")
    var keyword: String = "",
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Int = 0
) : Serializable {
    companion object {
        const val DEFAULT_ICON_NAME = "others"
    }
}
