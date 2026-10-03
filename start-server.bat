@echo off
chcp 65001 > nul
title Tank2D Server Launcher
echo ========================================================
echo   HE THONG KHOI DONG TU DONG TANK 2D ONLINE SERVER
echo ========================================================

:: 1. Kiem tra MySQL service (Port 3306)
echo [1/3] Dang kiem tra trang thai Co so du lieu MySQL...
netstat -ano | findstr :3306 > nul
if %errorlevel% equ 0 (
    echo [OK] Co so du lieu MySQL da hoat dong tren cong 3306.
) else (
    echo [..] MySQL chua chay! Dang tu dong kich hoat MySQL...
    net start mysql > nul 2>&1
    if %errorlevel% neq 0 (
        if exist "C:\xampp\mysql\bin\mysqld.exe" (
            start "" /B "C:\xampp\mysql\bin\mysqld.exe" --defaults-file="C:\xampp\mysql\bin\my.ini" --standalone
        ) else if exist "D:\xampp\mysql\bin\mysqld.exe" (
            start "" /B "D:\xampp\mysql\bin\mysqld.exe" --defaults-file="D:\xampp\mysql\bin\my.ini" --standalone
        ) else (
            echo [CANH BAO] Khong tim thay mysqld.exe cua XAMPP. Vui long bat MySQL bang tay tren XAMPP!
        )
    )
    timeout /t 3 /nobreak > nul
)

:: 2. Cho den khi MySQL mo cong 3306 hoan toan
echo [2/3] Xac thuc tinh san sang cua MySQL...
:WAIT_MYSQL
netstat -ano | findstr :3306 > nul
if %errorlevel% neq 0 (
    echo     Dang cho MySQL khoi dong...
    timeout /t 1 /nobreak > nul
    goto WAIT_MYSQL
)
echo [OK] MySQL da san sang phuc vu!

:: 3. Khoi dong Game Server
echo [3/3] Dang khoi dong Tank2D Game Server...
echo ========================================================
if exist "target\Server.jar" (
    java -jar target\Server.jar
) else if exist "Server.jar" (
    java -jar Server.jar
) else (
    echo [LOI] Khong tim thay Server.jar! Hay chay lenh 'mvn clean package' truoc.
)
pause
