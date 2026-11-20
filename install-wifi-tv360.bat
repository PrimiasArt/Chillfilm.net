@echo off
chcp 65001 >nul
title Cai dat Phyll Film Hub len TV360 Box qua Wi-Fi
set "ADB=F:\Project\.android-toolchain\android-sdk\platform-tools\adb.exe"
set "APK=%~dp0dist\PhyllFilmHub-universal.apk"

echo ========================================================
echo   CAI DAT PHYLL FILM HUB LEN TV360 BOX QUA CUNG WI-FI
echo ========================================================
echo.
if not exist "%ADB%" (
    echo [LOI] Khong tim thay adb.exe tai %ADB%
    pause
    exit /b 1
)
if not exist "%APK%" (
    echo [LOI] Khong tim thay file APK tai %APK%
    pause
    exit /b 1
)

echo Huong dan nhanh tren TV360 Box:
echo  1. Vao Cai dat -^> Tuy chon thiet bi -^> Gioi thieu -^> Bam OK 7 lan vao "Ban dung (Build)"
echo  2. Quay lai Tuy chon nha phat trien -^> Bat "Go loi USB (USB Debugging)"
echo  3. Xem IP cua TV trong Cai dat -^> Mang va Internet (VD: 192.168.101.25)
echo.
set /p TV_IP="Nhap dia chi IP cua TV360 Box (VD: 192.168.101.25 hoac IP:PORT): "

if "%TV_IP%"=="" (
    echo Ban chua nhap IP!
    pause
    exit /b 1
)

echo.
echo [1/3] Dang ket noi toi %TV_IP% ...
"%ADB%" disconnect >nul 2>&1
"%ADB%" connect %TV_IP%

echo.
echo ^>^>^> HAY NHIN LEN MAN HINH TV360: Neu hien bang hoi "Cho phep go loi USB?",
echo ^>^>^> hay tich chon "Luon cho phep" va bam OK tren Remote TV, sau do bam Enter tai day!
pause

"%ADB%" connect %TV_IP%
echo.
echo [2/3] Dang cai dat PhyllFilmHub-universal.apk (13.5 MB) len TV360 Box...
"%ADB%" install -r "%APK%"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [LOI] Cai dat chua thanh cong. Hay kiem tra lai IP hoac bam Cho phep tren man hinh TV!
    pause
    exit /b 1
)

echo.
echo [3/3] Cai dat THANH CONG! Dang mo Phyll Film Hub tren TV360 Box...
"%ADB%" shell am start -n com.phyll.filmhub/.MainActivity
echo.
echo Hoan tat! Chuc ban xem phim vui ve!
pause
