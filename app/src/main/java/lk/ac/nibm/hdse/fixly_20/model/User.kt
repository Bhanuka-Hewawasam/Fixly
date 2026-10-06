package lk.ac.nibm.hdse.fixly_20.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val roles: List<String> = listOf("customer"),
    val serviceCategories: List<String> = emptyList(),
    val certificateUrl: String? = null,
    val isVerified: Boolean = false
)
