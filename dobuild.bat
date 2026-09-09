@echo off
set JAVA_HOME=C:\Users\thwin\.jdks\jdk-21.0.12.1+1
set ANDROID_HOME=C:\Users\thwin\.androidsdk
set PATH=C:\Users\thwin\.jdks\jdk-21.0.12.1+1\bin;%PATH%
cd /d "C:\Users\thwin\Downloads\Telegram Desktop\blanc-coffee"
call "C:\Users\thwin\.jdks\gradlehome\gradle-9.3.1\bin\gradle.bat" :app:compileDebugKotlin --console=plain -x lint > runbuild.log 2>&1

