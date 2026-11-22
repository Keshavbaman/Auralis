-keepattributes Signature
-keep class app.auralis.data.** { *; }
-keep class app.auralis.domain.** { *; }

-keep class app.auralis.cast.AuralisCastOptions { *; }

# Vosk on-device speech recognition calls native code through JNA. R8 must not rename or strip
# JNA or Vosk members, or the native library cannot find the fields it reads (Pointer.peer).
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Structure { *; }
-keep class * implements com.sun.jna.Callback { *; }
-keep class * implements com.sun.jna.Library { *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**
