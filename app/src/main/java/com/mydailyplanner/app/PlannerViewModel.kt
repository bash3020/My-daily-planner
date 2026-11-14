package com.mydailyplanner.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mydailyplanner.app.data.ActivityItem
import com.mydailyplanner.app.data.AppDatabase
import com.mydailyplanner.app.util.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlannerViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application = application
    private val dao = AppDatabase.get(application).activityDao()

    val activities: StateFlow<List<ActivityItem>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedDate = MutableStateFlow(TimeUtils.startOfDay(System.currentTimeMillis()))

    fun selectDate(millis: Long) {
        selectedDate.value = TimeUtils.startOfDay(millis)
    }

    fun getActivity(id: Long): ActivityItem? =
        activities.value.firstOrNull { it.id == id }

    fun conflictsFor(candidate: ActivityItem): List<ActivityItem> =
        TimeUtils.findConflicts(candidate, activities.value)

    // ---------- Add / edit / delete ----------

    private fun nextStart(base: Long, recurrence: String, i: Int, customDays: Int): Long =
        when (recurrence) {
            "Daily" -> TimeUtils.addDays(base, i)
            "Weekly" -> TimeUtils.addDays(base, 7 * i)
            "Monthly" -> TimeUtils.addMonths(base, i)
            "Custom" -> TimeUtils.addDays(base, customDays * i)
            else -> base
        }

    fun saveActivity(
        item: ActivityItem,
        repeatCount: Int = 1,
        customDays: Int = 1,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            if (item.id != 0L) {
                dao.update(item)
                ReminderScheduler.schedule(app, item)
            } else {
                val repeating = item.recurrence != "None" && repeatCount > 1
                val firstId = dao.insert(item.copy(id = 0))
                val first = item.copy(id = firstId, seriesId = if (repeating) firstId else 0)
                dao.update(first)
                ReminderScheduler.schedule(app, first)

                if (repeating) {
                    val duration = item.endMillis - item.startMillis
                    for (i in 1 until repeatCount) {
                        val start = nextStart(item.startMillis, item.recurrence, i, customDays)
                        val copy = item.copy(
                            id = 0,
                            startMillis = start,
                            endMillis = start + duration,
                            seriesId = firstId,
                            status = "Pending",
                            actualStartMillis = null,
                            actualEndMillis = null
                        )
                        val newId = dao.insert(copy)
                        ReminderScheduler.schedule(app, copy.copy(id = newId))
                    }
                }
            }
            onDone()
        }
    }

    fun deleteActivity(item: ActivityItem, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            ReminderScheduler.cancel(app, item.id)
            dao.delete(item)
            onDone()
        }
    }

    // ---------- Status and time tracking ----------

    fun setCompleted(item: ActivityItem, completed: Boolean) {
        viewModelScope.launch {
            val updated = item.copy(status = if (completed) "Completed" else "Pending")
            dao.update(updated)
            ReminderScheduler.schedule(app, updated)
        }
    }

    fun startActivity(item: ActivityItem) {
        viewModelScope.launch {
            val updated = item.copy(
                actualStartMillis = System.currentTimeMillis(),
                actualEndMillis = null,
                status = "Pending"
            )
            dao.update(updated)
            ReminderScheduler.cancel(app, item.id)
        }
    }

    fun finishActivity(item: ActivityItem) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val updated = item.copy(
                actualStartMillis = item.actualStartMillis ?: item.startMillis,
                actualEndMillis = now,
                status = "Completed"
            )
            dao.update(updated)
            ReminderScheduler.cancel(app, item.id)
        }
    }

    fun setActualTimes(item: ActivityItem, start: Long?, end: Long?) {
        viewModelScope.launch {
            val completed = start != null && end != null
            val updated = item.copy(
                actualStartMillis = start,
                actualEndMillis = end,
                status = if (completed) "Completed" else item.status
            )
            dao.update(updated)
            if (completed) ReminderScheduler.cancel(app, item.id)
        }
    }

    // ---------- Backup and restore ----------

    suspend fun snapshot(): List<ActivityItem> = dao.getAll()

    fun restore(items: List<ActivityItem>, replaceExisting: Boolean, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            if (replaceExisting) {
                dao.getAll().forEach { ReminderScheduler.cancel(app, it.id) }
                dao.deleteAll()
            }
            dao.insertAll(items)
            ReminderScheduler.rescheduleAll(app, items)
            onDone()
        }
    }
}
