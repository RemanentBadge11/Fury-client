@echo off
setlocal EnableDelayedExpansion
set DIR=%~dp0
set GRADLE_USER_HOME=%USERPROFILE%\.gradle
"%DIR%gradle\gradle-9.2.0\bin\gradle" %*