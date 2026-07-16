package com.crmapplication.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DueReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val leadId = inputData.getString(KEY_LEAD_ID) ?: return Result.failure()
        val leadName = inputData.getString(KEY_LEAD_NAME).orEmpty().ifBlank { "this lead" }
        ReminderNotifier.notifyReminder(applicationContext, leadId, leadName)
        return Result.success()
    }

    companion object {
        const val KEY_LEAD_ID = "lead_id"
        const val KEY_LEAD_NAME = "lead_name"
    }
}
