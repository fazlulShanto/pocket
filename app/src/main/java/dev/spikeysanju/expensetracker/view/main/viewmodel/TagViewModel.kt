package dev.spikeysanju.expensetracker.view.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.spikeysanju.expensetracker.model.Tag
import dev.spikeysanju.expensetracker.repo.TagRepo
import dev.spikeysanju.expensetracker.repo.TransactionRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TagViewModel @Inject constructor(
    private val tagRepo: TagRepo,
    private val transactionRepo: TransactionRepo
) : ViewModel() {

    private val _tags = MutableStateFlow<List<Tag>>(emptyList())
    val tags: StateFlow<List<Tag>> = _tags

    private val _message = MutableSharedFlow<String>()
    val message = _message.asSharedFlow()

    init {
        getAllTags()
    }

    fun getAllTags() {
        viewModelScope.launch(Dispatchers.IO) {
            tagRepo.getAllTags().collect { tags ->
                _tags.value = tags.sortedByDescending { it.tagType }
            }
        }
    }

    fun insertTag(tag: Tag) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = tagRepo.getTagCountByNameAndType(tag.tagName, tag.tagType)
            if (count > 0) {
                _message.emit("Tag '${tag.tagName}' with type '${tag.tagType}' already exists.")
            } else {
                tagRepo.insert(tag)
                _message.emit("Tag added successfully")
            }
        }
    }

    fun updateTag(tag: Tag) {
        viewModelScope.launch(Dispatchers.IO) {
            tagRepo.update(tag)
        }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = transactionRepo.getTransactionCountByTagName(tag.tagName)
            if (count > 0) {
                _message.emit("Cannot delete tag '${tag.tagName}' because it is used in $count transactions.")
            } else {
                tagRepo.delete(tag)
                _message.emit("Tag deleted successfully")
            }
        }
    }
}
