package lk.ac.nibm.hdse.fixly_20.model

object BookingPolicy {
    fun canAssign(jobStatus: String?, activeLockExists: Boolean): Boolean =
        jobStatus == "Open" && !activeLockExists

    fun shouldReleaseLock(jobStatus: String?, customerConfirmed: Boolean): Boolean =
        jobStatus == "Completed" && customerConfirmed
}
