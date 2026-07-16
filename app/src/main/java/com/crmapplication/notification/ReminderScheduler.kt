package com.crmapplication.notification

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)

    fun schedule(leadId: String, leadName: String, dueMillis: Long) {
        val delay = dueMillis - System.currentTimeMillis()
        if (delay <= 0) {
            cancel(leadId)
            return
        }
        val data = Data.Builder()
            .putString(DueReminderWorker.KEY_LEAD_ID, leadId)
            .putString(DueReminderWorker.KEY_LEAD_NAME, leadName)
            .build()
        val request = OneTimeWorkRequestBuilder<DueReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()
        workManager.enqueueUniqueWork(workName(leadId), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(leadId: String) {
        workManager.cancelUniqueWork(workName(leadId))
    }

    private fun workName(leadId: String) = "due_reminder_$leadId"
}
