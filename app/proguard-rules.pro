# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
