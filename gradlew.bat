@echo off
setlocal
set VERSION=8.13
set DIST_NAME=gradle-%VERSION%-bin
set GRADLE_HOME=%USERPROFILE%\.gradle\wrapper\dists\morse-luz\%DIST_NAME%\gradle-%VERSION%

if exist "%GRADLE_HOME%\bin\gradle.bat" goto runGradle
where gradle >nul 2>nul
if not errorlevel 1 (
  set FOUND_VERSION=
  for /f "tokens=2" %%G in ('gradle --version ^| findstr /b "Gradle "') do set FOUND_VERSION=%%G
  if "%FOUND_VERSION%"=="%VERSION%" (
    gradle %*
    exit /b %errorlevel%
  )
)

echo Downloading Gradle %VERSION% for the first build...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $version='%VERSION%'; $gradleHome='%GRADLE_HOME%'; $dir=Split-Path -Parent $gradleHome; New-Item -ItemType Directory -Force -Path $dir | Out-Null; $zip=Join-Path $dir ('gradle-' + $version + '-bin.zip'); if (!(Test-Path $zip)) { Invoke-WebRequest -Uri ('https://services.gradle.org/distributions/gradle-' + $version + '-bin.zip') -OutFile $zip }; if (!(Test-Path (Join-Path $gradleHome 'bin/gradle.bat'))) { $expected='20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78'; $actual=(Get-FileHash -Path $zip -Algorithm SHA256).Hash.ToLower(); if ($actual -ne $expected) { Remove-Item $zip -Force; throw 'Gradle SHA-256 mismatch' }; Expand-Archive -Path $zip -DestinationPath $dir -Force }"
if errorlevel 1 exit /b %errorlevel%

:runGradle
"%GRADLE_HOME%\bin\gradle.bat" %*
exit /b %errorlevel%
