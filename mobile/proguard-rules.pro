# Tesseract и Leptonica обращаются к полям Java-классов из нативного кода — их нельзя переименовывать.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
