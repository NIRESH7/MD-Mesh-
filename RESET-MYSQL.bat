@echo off
:: Double-click this file. Click Yes on the Admin popup.
powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-File','C:\Users\Admin\Desktop\MD Mesh\reset-mysql-password.ps1'"
