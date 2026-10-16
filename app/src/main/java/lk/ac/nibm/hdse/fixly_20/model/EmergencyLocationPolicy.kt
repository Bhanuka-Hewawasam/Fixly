package lk.ac.nibm.hdse.fixly_20.model

object EmergencyLocationPolicy {
    fun canReadCurrentLocation(hasFinePermission: Boolean, locationServicesEnabled: Boolean): Boolean =
        hasFinePermission && locationServicesEnabled
}
