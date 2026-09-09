$J = "$env:USERPROFILE\.jdks\jdk-21.0.12.1+1"
$SDK = "C:\Users\thwin\.androidsdk"
$P = 'C:\Users\thwin\Downloads\Telegram Desktop\blanc-coffee'
$bat = "@echo off`r`nset JAVA_HOME=$J`r`nset ANDROID_HOME=$SDK`r`nset PATH=$J\bin;%PATH%`r`ncd /d `"$P`"`r`n"
$bat += "`"$SDK\cmdline-tools\latest\bin\sdkmanager.bat`" --licenses --sdk_root=`"$SDK`" < yesin.txt > lic.log 2>&1`r`n"
$bat += "`"$SDK\cmdline-tools\latest\bin\sdkmanager.bat`" `"platform-tools`" `"platforms;android-36.1`" `"build-tools;36.0.0`" --sdk_root=`"$SDK`" >> sdkinstall.log 2>&1`r`n"
Set-Content -Path "$P\dosdk.bat" -Value $bat -Encoding Ascii
Start-Process -FilePath "$P\dosdk.bat" -WindowStyle Hidden
Start-Sleep -Seconds 28
Get-Content "$P\sdkinstall.log" -Tail 6 -ErrorAction SilentlyContinue
Write-Output "---tail done---"