@echo off
echo Starting Spread daemon...

cd ..\spread-bin-4.0.0\bin\win32

spread.exe -n node3 -c spread.conf

pause
