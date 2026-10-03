package lk.ac.nibm.hdse.fixly_20.model

/**
 * Represents a Fixly user document stored in Firestore under "users/{uid}".
 * roles can contain "customer" and/or "provider" — a user can be both.
 */
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val roles: List<String> = listOf("customer"),
    val certificateUrl: String? = null,
    val isVerified: Boolean = false
)
