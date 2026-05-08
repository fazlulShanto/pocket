package dev.spikeysanju.expensetracker.repo

import dev.spikeysanju.expensetracker.data.local.AppDatabase
import dev.spikeysanju.expensetracker.model.Tag
import javax.inject.Inject

class TagRepo @Inject constructor(private val db: AppDatabase) {

    suspend fun insert(tag: Tag) = db.getTagDao().insertTag(tag)

    suspend fun update(tag: Tag) = db.getTagDao().updateTag(tag)

    suspend fun delete(tag: Tag) = db.getTagDao().deleteTag(tag)

    fun getAllTags() = db.getTagDao().getAllTags()

    fun getTagsByType(type: String) = db.getTagDao().getTagsByType(type)

    suspend fun getTagCountByNameAndType(name: String, type: String) = db.getTagDao().getTagCountByNameAndType(name, type)
}
