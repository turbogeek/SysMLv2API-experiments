@echo off
REM LaunchExplorer.bat
REM Launches the SysMLv2 Explorer GUI

echo ===================================
echo  SysML v2 API Explorer
echo ===================================
echo.
echo Starting GUI...
echo.
echo TIP: Select one of these projects with data:
echo   - Standard Libraries1 (94 root elements)
echo   - 3DS SysML Customization (98 elements)
echo   - CATIA Magic SysML v2 v2026x Release (98 elements)
echo   - variation and variant in SysMLv2 (98 elements)
echo.

cd /d "%~dp0"
groovy SysMLv2Explorer.groovy

if errorlevel 1 (
    echo.
    echo ERROR: Failed to launch GUI
    echo.
    echo Make sure Groovy is installed and in your PATH
    echo Press any key to exit...
    pause > nul
)
