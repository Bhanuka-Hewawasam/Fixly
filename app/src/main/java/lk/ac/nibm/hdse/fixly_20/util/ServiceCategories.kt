package lk.ac.nibm.hdse.fixly_20.util

object ServiceCategories {
    val canonicalList = listOf(
        "Plumbing",
        "Electrical",
        "Cleaning",
        "Carpentry",
        "Painting",
        "AC Repair",
        "Gardening",
        "Vehicle Mechanic",
        "Computer Technician",
        "Other"
    )

    fun normalize(name: String): String {
        val trimmed = name.trim()
        return when (trimmed.lowercase()) {
            "plumber" -> "Plumbing"
            "electrician" -> "Electrical"
            "ac technician" -> "AC Repair"
            else -> {
                canonicalList.find { it.equals(trimmed, ignoreCase = true) } ?: trimmed
            }
        }
    }
}
