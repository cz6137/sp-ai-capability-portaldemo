@echo off
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "S:\work\ai平台\sp-ai-portal\sp-ai-portal\scripts\start-meeting-minutes-demo.ps1" -SkipProviderTest
if errorlevel 1 pause
