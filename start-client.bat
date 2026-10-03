@echo off
chcp 65001 > nul
title Tank2D Client
echo Dang khoi dong Tank 2D Online Client...
if exist "target\Client.jar" (
    java -jar target\Client.jar
) else if exist "Client.jar" (
    java -jar Client.jar
) else (
    echo [LOI] Khong tim thay Client.jar!
    pause
)
