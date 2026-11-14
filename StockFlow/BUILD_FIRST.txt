STOCKFLOW - JDK 17.0.12 BUILD PACKAGE

This package's Windows build script checks specifically for Java/JDK 17.0.12.
It does not silently accept Java 21 or Java 25.

BEFORE BUILDING
1. Install 64-bit Python 3.12 (or 3.11) and select "Python Launcher".
2. Install 64-bit JDK 17.0.12.
3. Set Windows environment variable JDK17_HOME (recommended) or JAVA_HOME
   to the JDK installation folder - the folder that contains the bin folder.
   Example: C:\Program Files\Eclipse Adoptium\jdk-17.0.12.7-hotspot
   Do NOT set it to the bin folder or java.exe.
4. In Android Studio > SDK Manager, install Android SDK Platform 35 and
   Android SDK Build-Tools 35.0.0.
5. Extract this entire StockFlow folder to a normal folder, e.g. C:\StockFlow.
   Do not run it from inside the ZIP file.
6. Double-click build_windows.bat. Keep the command window open.
   The first build needs internet to download packages and Gradle.

OUTPUT
- dist\StockFlow.exe
- dist\StockFlow-Scanner.apk

The Android APK is a debug/testing APK, not a Play Store release.
The script builds the Windows EXE first, then the Android APK. If Android
SDK setup fails, the Windows EXE may still have been created in dist.

DATA SAFETY
Do not delete the old StockFlow app or this data folder:
%LOCALAPPDATA%\StockFlow
Keep it until the replacement has been tested.

TROUBLESHOOTING
If the script says JDK 17.0.12 was not found, check that JDK17_HOME points
to the JDK root folder and that this file exists:
%JDK17_HOME%\bin\java.exe

If the build fails, take a screenshot showing the final error lines before
closing the command window.
