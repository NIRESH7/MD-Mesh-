@echo off
cd /d "C:\Users\Admin\Desktop\MD Mesh\backend"
call npm run seed
if errorlevel 1 (
  echo SEED FAILED
  pause
  exit /b 1
)
echo.
echo Open http://localhost:5000
echo Login: admin / password123
echo.
call npm start
pause
