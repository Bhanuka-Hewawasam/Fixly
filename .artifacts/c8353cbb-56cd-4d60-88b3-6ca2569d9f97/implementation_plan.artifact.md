# GPS Live Location Tracking Implementation Plan

Implement real-time GPS live location tracking between Service Provider and Customer during accepted/in-progress jobs.

## Proposed Changes

### [Manifest & Model]
- #### [MODIFY] [AndroidManifest.xml](file:///D:/Fixly_2.0/app/src/main/AndroidManifest.xml)
  - Add Google Maps API key meta-data inside `<application>`.
- #### [MODIFY] [JobPost.kt](file:///D:/Fixly_2.0/app/src/main/java/lk/ac/nibm/hdse/fixly_20/model/JobPost.kt)
  - Add `assignedProviderId` and `providerLocation` (`GeoPoint?`).

### [Repositories]
- #### [MODIFY] [BidRepository.kt](file:///D:/Fixly_2.0/app/src/main/java/lk/ac/nibm/hdse/fixly_20/data/repository/BidRepository.kt)
  - Update `selectBid` to set status to "Assigned" and assign provider.
- #### [MODIFY] [JobRepository.kt](file:///D:/Fixly_2.0/app/src/main/java/lk/ac/nibm/hdse/fixly_20/data/repository/JobRepository.kt)
  - Add `observeJob`, `observeProviderAssignedJobs`, `updateProviderLocation`, `updateJobStatus`.

### [ViewModels & Screens]
- #### [NEW] [AcceptedJobsViewModel.kt](file:///D:/Fixly_2.0/app/src/main/java/lk/ac/nibm.hdse.fixly_20/viewmodel/AcceptedJobsViewModel.kt)
- #### [NEW] [AcceptedJobsScreen.kt](file:///D:/Fixly_2.0/app/src/main/java/lk/ac/nibm.hdse.fixly_20/ui/screens/AcceptedJobsScreen.kt)
- #### [NEW] [ProviderTrackingViewModel.kt](file:///D:/Fixly_2.0/app/src/main/java/lk/ac/nibm.hdse.fixly_20/viewmodel/ProviderTrackingViewModel.kt)
- #### [NEW] [ProviderTrackingScreen.kt](file:///D:/Fixly_2.0/app/src/main/java/lk/ac/nibm.hdse.fixly_20/ui/screens/ProviderTrackingScreen.kt)
- #### [NEW] [CustomerTrackingViewModel.kt](file:///D:/Fixly_2.0/app/src/main/java/lk/ac/nibm.hdse.fixly_20/viewmodel/CustomerTrackingViewModel.kt)
- #### [NEW] [CustomerTrackingScreen.kt](file:///D:/Fixly_2.0/app/src/main/java/lk/ac/nibm.hdse.fixly_20/ui/screens/CustomerTrackingScreen.kt)
- #### [MODIFY] [MyBookingsScreen.kt](file:///D:/Fixly_2.0/app/src/main/java/lk/ac/nibm.hdse.fixly_20/ui/screens/MyBookingsScreen.kt)
- #### [MODIFY] [AppNavigation.kt](file:///D:/Fixly_2.0/app/src/main/java/lk/ac/nibm.hdse.fixly_20/navigation/AppNavigation.kt)

## Verification Plan
- Build project successfully (`app:assembleDebug`).
