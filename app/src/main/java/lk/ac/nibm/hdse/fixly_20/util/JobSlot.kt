package lk.ac.nibm.hdse.fixly_20.util

import lk.ac.nibm.hdse.fixly_20.model.JobPost
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

const val JOB_DURATION_HOURS = 2

data class JobTimeWindow(
    val startMillis: Long,
    val endMillis: Long
)

fun parseJobTimeWindow(preferredDate: String, preferredTime: String): JobTimeWindow? {
    if (preferredDate.isBlank() || preferredTime.isBlank()) return null
    val formatter = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).apply {
        isLenient = false
    }
    val input = "${preferredDate.trim()} ${preferredTime.trim()}"
    val position = ParsePosition(0)
    val start = formatter.parse(input, position)?.time
        ?.takeIf { position.index == input.length }
        ?: return null
    return JobTimeWindow(
        startMillis = start,
        endMillis = start + JOB_DURATION_HOURS * 60L * 60L * 1000L
    )
}

fun providerJobSlotKeys(providerId: String, preferredDate: String, preferredTime: String): List<String> {
    val window = parseJobTimeWindow(preferredDate, preferredTime) ?: return emptyList()
    val dateFormatter = SimpleDateFormat("yyyyMMdd", Locale.US)
    val hourFormatter = SimpleDateFormat("HH", Locale.US)
    val calendar = Calendar.getInstance().apply { timeInMillis = window.startMillis }
    val slotKeys = linkedSetOf<String>()
    while (calendar.timeInMillis < window.endMillis) {
        slotKeys += "${providerId}_${dateFormatter.format(calendar.time)}_${hourFormatter.format(calendar.time)}"
        calendar.add(Calendar.HOUR_OF_DAY, 1)
    }
    return slotKeys.toList()
}

fun jobsOverlap(first: JobPost, second: JobPost): Boolean {
    if (first.isEmergency || second.isEmergency) return false
    val firstWindow = parseJobTimeWindow(first.preferredDate, first.preferredTime) ?: return false
    val secondWindow = parseJobTimeWindow(second.preferredDate, second.preferredTime) ?: return false
    return firstWindow.startMillis < secondWindow.endMillis &&
        secondWindow.startMillis < firstWindow.endMillis
}

fun findOverlappingActiveJob(candidate: JobPost, activeJobs: List<JobPost>): JobPost? =
    if (candidate.isEmergency) {
        null
    } else {
        activeJobs.firstOrNull { activeJob ->
            activeJob.status in listOf("Accepted", "In Progress") && jobsOverlap(candidate, activeJob)
        }
    }

fun jobTimeConflictMessage(job: JobPost): String =
    "You already have a job at this time (${job.category}, ${job.preferredDate} ${job.preferredTime})"
