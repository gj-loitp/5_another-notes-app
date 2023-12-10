package com.roy93group.notes.receiver

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.roy93group.notes.model.ReminderAlarmCallback
import com.roy93group.notes.model.ReminderAlarmManager
import javax.inject.Inject

/**
 * Implementation of the alarm callback for [ReminderAlarmManager].
 * Uses the app context to set alarms broadcasted to [AlarmReceiver].
 */
class ReceiverAlarmCallback @Inject constructor(
    private val context: Context,
) : ReminderAlarmCallback {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @SuppressLint("ScheduleExactAlarm")
    override fun addAlarm(noteId: Long, time: Long) {
        val alarmIntent = getAlarmPendingIndent(noteId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, time, alarmIntent
            )
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, time, alarmIntent)
        }
    }

    override fun removeAlarm(noteId: Long) {
        getAlarmPendingIndent(noteId).cancel()
    }

    private fun getAlarmPendingIndent(noteId: Long): PendingIntent {
        // Make alarm intent
        val receiverIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM
            putExtra(AlarmReceiver.EXTRA_NOTE_ID, noteId)
        }
        var flags = 0
        if (Build.VERSION.SDK_INT >= 23) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, noteId.toInt(), receiverIntent, flags)
    }
}