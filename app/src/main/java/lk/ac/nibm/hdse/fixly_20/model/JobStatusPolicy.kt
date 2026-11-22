package lk.ac.nibm.hdse.fixly_20.model

object JobStatusPolicy {
    fun canProviderTransition(
        authenticatedProviderId: String,
        assignedProviderId: String?,
        currentStatus: String?,
        nextStatus: String,
        hasAfterPhoto: Boolean
    ): Boolean =
        authenticatedProviderId.isNotBlank() &&
            authenticatedProviderId == assignedProviderId &&
            ((currentStatus in listOf("Accepted", "Assigned") && nextStatus == "In Progress") ||
                (currentStatus == "In Progress" && nextStatus == "Completed" && hasAfterPhoto))
}
