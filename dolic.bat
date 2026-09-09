@echo off
set JAVA_HOME=C:\Users\thwin\.jdks\jdk-21.0.12.1+1
set ANDROID_HOME=C:\Users\thwin\.androidsdk
set PATH=C:\Users\thwin\.jdks\jdk-21.0.12.1+1\bin;%PATH%
cd /d "C:\Users\thwin\Downloads\Telegram Desktop\blanc-coffee"
"C:\Users\thwin\.androidsdk\cmdline-tools\latest\bin\sdkmanager.bat" --licenses --sdk_root="C:\Users\thwin\.androidsdk" < yesin.txt > lic.log 2>&1

