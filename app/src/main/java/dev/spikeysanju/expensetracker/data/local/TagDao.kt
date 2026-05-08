package dev.spikeysanju.expensetracker.data.local

import androidx.room.*
import dev.spikeysanju.expensetracker.model.Tag
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: Tag)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<Tag>)

    @Update
    suspend fun updateTag(tag: Tag)

    @Delete
    suspend fun deleteTag(tag: Tag)

    @Query("SELECT * FROM tags")
    fun getAllTags(): Flow<List<Tag>>

    @Query("SELECT * FROM tags")
    suspend fun getAllTagsSnapshot(): List<Tag>

    @Query("DELETE FROM tags")
    suspend fun deleteAllTags()

    @Query("SELECT * FROM tags WHERE tagType = :type")
    fun getTagsByType(type: String): Flow<List<Tag>>

    @Query("SELECT COUNT(*) FROM tags WHERE tagName = :name AND tagType = :type")
    suspend fun getTagCountByNameAndType(name: String, type: String): Int
}
