/*
 * Copyright 2020 Nicolas Maltais
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:UseSerializers(DateTimeConverter::class, RecurrenceConverter::class)

package com.maltaisn.notes.model.entity

import com.maltaisn.notes.model.converter.DateTimeConverter
import com.maltaisn.notes.model.converter.RecurrenceConverter
import com.maltaisn.recurpicker.Recurrence
import com.maltaisn.recurpicker.RecurrenceFinder
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.Date

/**
 * A reminder for a [Note].
 */
@Serializable
data class Reminder(

    /**
     * The start time of this reminder, in local time zone.
     */
    val start: Date,

    /**
     * A RFC 5545 RRule describing the recurrence rule of this reminder.
     * Can be `null` to indicate that the reminder isn't recurring.
     */
    val recurrence: Recurrence?,

    /**
     * The time of the next occurence of this reminder in local time zone.
     * For non-recurring reminders, this can also be the time that the reminder was postponed to.
     * When reminder is marked as [done], this keeps track of the last occurence time.
     */
    val next: Date,

    /**
     * The number of times this reminder has occured as of [next] date. This is needed for recurrence events
     * calculation. For non-recurring reminders, this should always be 1.
     */
    val count: Int,

    /**
     * Whether the last occurence of this reminder was marked as done by user.
     */
    val done: Boolean
) {

    init {
        require(count > 0) { "Count must be greater than zero." }
        require(recurrence != null || count == 1) { "Count should be 1 if non-recurring." }
    }

    /**
     * Create a reminder with the [next] property updated to the next occurence of this reminder.
     * [done] is always set to `false`. If recurrence is not recurring or recurrence is done, the
     * same instance is returned.
     */
    fun findNextReminder(recurrenceFinder: RecurrenceFinder): Reminder =
        if (recurrence == null) {
            // Not recurring.
            this
        } else {
            // Find next occurence based on the last.
            val found = recurrenceFinder.findBasedOn(recurrence, start.time, next.time,
                count, 1, includeStart = false)
            if (found.isEmpty()) {
                // Recurrence is done.
                this
            } else {
                copy(next = Date(found.first()), count = count + 1, done = false)
            }
        }

    /**
     * Postpone this non-recurring reminder to a future [date].
     */
    fun postponeTo(date: Date): Reminder {
        require(recurrence == null) { "Cannot postpone recurring reminder." }
        require(!done) { "Cannot postpone reminder marked as done." }
        require(date.time > next.time) { "Postponed time must be after current time." }
        return copy(next = date)
    }

    /**
     * Mark this reminder as done.
     */
    fun markAsDone() = copy(done = true)

    companion object {

        fun create(start: Date, recurrence: Recurrence?, recurrenceFinder: RecurrenceFinder): Reminder {
            val date = if (recurrence == null) {
                start
            } else {
                Date(recurrenceFinder.find(recurrence, start.time, 1).firstOrNull()
                    ?: throw IllegalArgumentException("Recurring reminder has no events."))
            }
            return Reminder(start, recurrence, date, 1, false)
        }
    }
}
