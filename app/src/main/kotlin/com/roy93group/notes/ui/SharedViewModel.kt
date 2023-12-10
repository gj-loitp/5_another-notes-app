package com.roy93group.notes.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roy93group.notes.R
import com.roy93group.notes.model.NotesRepository
import com.roy93group.notes.model.ReminderAlarmManager
import com.roy93group.notes.model.SortSettings
import com.roy93group.notes.model.entity.Label
import com.roy93group.notes.model.entity.NoteStatus
import com.roy93group.notes.model.entity.Reminder
import com.roy93group.notes.ui.navigation.HomeDestination
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared view model used to send Snackbars from a fragment being popped from backstack.
 */
class SharedViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
    private val reminderAlarmManager: ReminderAlarmManager,
) : ViewModel() {

    // No need to save this in saved state handle because Snackbar
    //
    private var lastStatusChange: StatusChange? = null

    private val _messageEvent = MutableLiveData<Event<Int>>()
    val messageEvent: LiveData<Event<Int>>
        get() = _messageEvent

    private val _statusChangeEvent = MutableLiveData<Event<StatusChange>>()
    val statusChangeEvent: LiveData<Event<StatusChange>>
        get() = _statusChangeEvent

    private val _reminderChangeEvent = MutableLiveData<Event<Reminder?>>()
    val reminderChangeEvent: LiveData<Event<Reminder?>>
        get() = _reminderChangeEvent

    // This is a bit wrong... events can only be observed once, but the same event
    // has to be observed twice here (label add event). Thus we need two LiveDatas... or a refactor.
    private val _labelAddEventNav = MutableLiveData<Event<Label>>()
    val labelAddEventNav: LiveData<Event<Label>>
        get() = _labelAddEventNav

    private val _labelAddEventSelect = MutableLiveData<Event<Label>>()
    val labelAddEventSelect: LiveData<Event<Label>>
        get() = _labelAddEventSelect

    private val _sortChangeEvent = MutableLiveData<Event<SortSettings>>()
    val sortChangeEvent: LiveData<Event<SortSettings>>
        get() = _sortChangeEvent

    private val _currentHomeDestination = MutableLiveData<HomeDestination>()
    val currentHomeDestination: LiveData<HomeDestination>
        get() = _currentHomeDestination

    private val _currentHomeDestinationChangeEvent = MutableLiveData<Event<Unit>>()
    val currentHomeDestinationChangeEvent: LiveData<Event<Unit>>
        get() = _currentHomeDestinationChangeEvent

    private val _sharedElementTransitionFinishedEvent = MutableLiveData<Event<Unit>>()
    val sharedElementTransitionFinishedEvent: LiveData<Event<Unit>>
        get() = _sharedElementTransitionFinishedEvent

    private val _noteCreatedEvent = MutableLiveData<Event<Long>>()
    val noteCreatedEvent: LiveData<Event<Long>>
        get() = _noteCreatedEvent

    fun onBlankNoteDiscarded() {
        // Not shown from EditFragment so that FAB is pushed up.
        _messageEvent.send(R.string.edit_message_blank_note_discarded)
    }

    fun onStatusChange(statusChange: StatusChange) {
        lastStatusChange = statusChange
        _statusChangeEvent.send(statusChange)
    }

    fun undoStatusChange() {
        val change = lastStatusChange ?: return
        viewModelScope.launch {
            notesRepository.updateNotes(change.oldNotes)
        }

        if (change.newStatus == NoteStatus.DELETED) {
            // Notes were deleted, removing any reminder alarm that had been set. Set them back.
            for (note in change.oldNotes) {
                if (note.reminder != null) {
                    reminderAlarmManager.setNoteReminderAlarm(note)
                }
            }
        }

        lastStatusChange = null
    }

    fun onReminderChange(reminder: Reminder?) {
        _reminderChangeEvent.send(reminder)
    }

    fun onLabelAdd(label: Label) {
        _labelAddEventNav.send(label)
        _labelAddEventSelect.send(label)
    }

    fun changeSortSettings(settings: SortSettings) {
        _sortChangeEvent.send(settings)
    }

    /**
     * This is the method to use whenever the home destination should be changed.
     * The navigation list selection will be updated automatically and the rest of app can listen for these changes.
     */
    fun changeHomeDestination(destination: HomeDestination) {
        _currentHomeDestination.value = destination
        _currentHomeDestinationChangeEvent.send()
    }

    fun sharedElementTransitionFinished() {
        _sharedElementTransitionFinishedEvent.send()
    }

    fun noteCreated(noteId: Long) {
        _noteCreatedEvent.send(noteId)
    }
}