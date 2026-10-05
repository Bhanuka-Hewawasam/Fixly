package lk.ac.nibm.hdse.fixly_20.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val roles: List<String> = listOf("customer"),
    val serviceCategories: List<String> = emptyList(), // e.g. ["Plumbing", "Electrical"] — only relevant when roles contains "provider"
    val certificateUrl: String? = null,
    val isVerified: Boolean = false
)
