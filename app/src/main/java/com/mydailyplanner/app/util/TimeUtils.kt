package com.mydailyplanner.app.util

import com.mydailyplanner.app.data.ActivityItem
import com.mydailyplanner.app.data.Categories
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeUtils {

    const val MINUTE = 60_000L

    fun plannedMinutes(a: ActivityItem): Long =
        maxOf(0L, (a.endMillis - a.startMillis) / MINUTE)

    fun actualMinutes(a: ActivityItem): Long {
        val s = a.actualStartMillis
        val e = a.actualEndMillis
        return if (s != null && e != null) maxOf(0L, (e - s) / MINUTE) else 0L
    }

    // ---------- Day, week, month boundaries ----------

    fun startOfDay(millis: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun addDays(millis: Long, days: Int): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        c.add(Calendar.DAY_OF_YEAR, days)
        return c.timeInMillis
    }

    fun addMonths(millis: Long, months: Int): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        c.add(Calendar.MONTH, months)
        return c.timeInMillis
    }

    fun startOfWeek(millis: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = startOfDay(millis)
        val diff = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
        c.add(Calendar.DAY_OF_YEAR, -diff)
        return c.timeInMillis
    }

    fun startOfMonth(millis: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = startOfDay(millis)
        c.set(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    fun isSameDay(a: Long, b: Long): Boolean = startOfDay(a) == startOfDay(b)

    // ---------- Text formatting ----------

    fun formatDuration(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "${m}m"
            m == 0L -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    fun formatHours(minutes: Long): String =
        String.format(Locale.ENGLISH, "%.1f h", minutes / 60.0)

    fun formatTime(millis: Long): String =
        SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(millis))

    fun formatDate(millis: Long): String =
        SimpleDateFormat("EEE, d MMM yyyy", Locale.ENGLISH).format(Date(millis))

    fun formatDateTime(millis: Long): String =
        SimpleDateFormat("EEE, d MMM, h:mm a", Locale.ENGLISH).format(Date(millis))

    // ---------- Status and conflicts ----------

    fun isOverdue(a: ActivityItem, now: Long): Boolean =
        a.status == "Pending" && a.endMillis < now

    fun isInProgress(a: ActivityItem): Boolean =
        a.status == "Pending" && a.actualStartMillis != null && a.actualEndMillis == null

    fun overlaps(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Boolean =
        aStart < bEnd && bStart < aEnd

    fun findConflicts(candidate: ActivityItem, all: List<ActivityItem>): List<ActivityItem> =
        all.filter {
            it.id != candidate.id &&
                overlaps(candidate.startMillis, candidate.endMillis, it.startMillis, it.endMillis)
        }

    fun occursOnDay(a: ActivityItem, dayStart: Long): Boolean {
        val dayEnd = addDays(dayStart, 1)
        return overlaps(a.startMillis, a.endMillis, dayStart, dayEnd) ||
            (a.startMillis >= dayStart && a.startMillis < dayEnd)
    }

    // ---------- Time totals (overlaps are not counted twice) ----------

    private fun mergedMinutes(ranges: List<Pair<Long, Long>>): Long {
        if (ranges.isEmpty()) return 0L
        val sorted = ranges.sortedBy { it.first }
        var total = 0L
        var curStart = sorted[0].first
        var curEnd = sorted[0].second
        for (i in 1 until sorted.size) {
            val s = sorted[i].first
            val e = sorted[i].second
            if (s <= curEnd) {
                if (e > curEnd) curEnd = e
            } else {
                total += curEnd - curStart
                curStart = s
                curEnd = e
            }
        }
        total += curEnd - curStart
        return total / MINUTE
    }

    private fun actualRanges(items: List<ActivityItem>, from: Long, to: Long): List<Pair<Long, Long>> =
        items.mapNotNull { a ->
            val s = a.actualStartMillis
            val e = a.actualEndMillis
            if (s == null || e == null) {
                null
            } else {
                val cs = maxOf(s, from)
                val ce = minOf(e, to)
                if (ce > cs) Pair(cs, ce) else null
            }
        }

    private fun plannedRanges(items: List<ActivityItem>, from: Long, to: Long): List<Pair<Long, Long>> =
        items.mapNotNull { a ->
            val cs = maxOf(a.startMillis, from)
            val ce = minOf(a.endMillis, to)
            if (ce > cs) Pair(cs, ce) else null
        }

    fun actualMinutesInRange(items: List<ActivityItem>, from: Long, to: Long): Long =
        mergedMinutes(actualRanges(items, from, to))

    fun plannedMinutesInRange(items: List<ActivityItem>, from: Long, to: Long): Long =
        mergedMinutes(plannedRanges(items, from, to))

    fun actualMinutesByCategory(items: List<ActivityItem>, from: Long, to: Long): Map<String, Long> =
        Categories.all.associateWith { cat ->
            mergedMinutes(actualRanges(items.filter { it.category == cat }, from, to))
        }
}
