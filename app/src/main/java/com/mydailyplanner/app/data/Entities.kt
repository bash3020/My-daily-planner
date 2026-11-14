package com.mydailyplanner.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activities")
data class ActivityItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val category: String = "Other",
    val startMillis: Long,
    val endMillis: Long,
    val location: String = "",
    val notes: String = "",
    val priority: String = "Medium",
    val reminderMinutes: Int = -1,
    val status: String = "Pending",
    val actualStartMillis: Long? = null,
    val actualEndMillis: Long? = null,
    val recurrence: String = "None",
    val seriesId: Long = 0
)

object Categories {
    val all = listOf(
        "Education",
        "Business",
        "Personal",
        "Travel and Going Out",
        "Worship",
        "Health",
        "Other"
    )
}
