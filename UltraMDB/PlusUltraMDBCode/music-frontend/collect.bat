@echo off
setlocal enabledelayedexpansion

REM ========================================
REM Scala Project File Collector
REM Collects all Scala-related files and outputs them in a format suitable for LLM context
REM ========================================

REM Configuration - modify these variables as needed
set "PROJECT_DIR=%~1"
set "OUTPUT_FILE=%~2"

REM Default values if parameters not provided
if "%PROJECT_DIR%"=="" set "PROJECT_DIR=."
if "%OUTPUT_FILE%"=="" set "OUTPUT_FILE=scala_project_context.txt"

REM Convert to absolute paths
pushd "%PROJECT_DIR%" 2>nul
if errorlevel 1 (
    echo Error: Directory "%PROJECT_DIR%" does not exist
    exit /b 1
)
set "PROJECT_DIR=%CD%"
popd

REM Folders to exclude
set EXCLUDE_DIRS=target .git .idea .vscode node_modules .metals .bloop bin out

echo Starting Scala project file collection...
echo Project directory: %PROJECT_DIR%
echo Output file: %OUTPUT_FILE%
echo.

REM Clear output file and add header
echo. > "%OUTPUT_FILE%"

set "FILE_COUNT=0"

REM Create temporary file for all files
set "ALL_FILES_TEMP=%TEMP%\scala_collector_%RANDOM%.tmp"

REM Collect all files at once using a single dir command with multiple patterns
echo Collecting all relevant files...
(
    dir /s /b "%PROJECT_DIR%\*.scala" 2>nul
    dir /s /b "%PROJECT_DIR%\*.sbt" 2>nul
    dir /s /b "%PROJECT_DIR%\*.properties" 2>nul
    dir /s /b "%PROJECT_DIR%\*.conf" 2>nul
    dir /s /b "%PROJECT_DIR%\*.hocon" 2>nul
    dir /s /b "%PROJECT_DIR%\*.yaml" 2>nul
    dir /s /b "%PROJECT_DIR%\*.yml" 2>nul
    dir /s /b "%PROJECT_DIR%\*.xml" 2>nul
    dir /s /b "%PROJECT_DIR%\*.json" 2>nul
    dir /s /b "%PROJECT_DIR%\*.md" 2>nul
    @REM dir /s /b "%PROJECT_DIR%\*.txt" 2>nul
    dir /s /b "%PROJECT_DIR%\*.gitignore" 2>nul
) > "%ALL_FILES_TEMP%"

REM Sort and remove duplicates
sort /unique "%ALL_FILES_TEMP%" > "%ALL_FILES_TEMP%.sorted"
move "%ALL_FILES_TEMP%.sorted" "%ALL_FILES_TEMP%" >nul 2>&1

REM Process each file
echo Processing files...
for /f "usebackq delims=" %%F in ("%ALL_FILES_TEMP%") do (
    set "CURRENT_FILE=%%F"
    set "SKIP_FILE=false"
    
    REM Check if file is in excluded directory
    for %%D in (%EXCLUDE_DIRS%) do (
        echo %%F | findstr /I "\\%%D\\" >nul 2>&1
        if !errorlevel! equ 0 set "SKIP_FILE=true"
    )
    
    REM Process file if not excluded
    if "!SKIP_FILE!"=="false" (
        if exist "%%F" (
            REM Calculate relative path
            set "REL_PATH=%%F"
            set "REL_PATH=!REL_PATH:%PROJECT_DIR%\=!"
            
            echo Processing: !REL_PATH!
            
            REM Increment counter
            set /A FILE_COUNT+=1
            
            REM Add file separator and header
            echo. >> "%OUTPUT_FILE%"
            echo ========== !REL_PATH! ========== >> "%OUTPUT_FILE%"
            
            REM Add file content with error handling
            type "%%F" >> "%OUTPUT_FILE%" 2>nul || (
                echo [Error: Could not read file content - file may be binary or locked] >> "%OUTPUT_FILE%"
            )
            
            REM Add file footer
            echo. >> "%OUTPUT_FILE%"
            echo ========== End of !REL_PATH! ========== >> "%OUTPUT_FILE%"
        )
    )
)

REM Add footer
echo. >> "%OUTPUT_FILE%"
echo ======================================== >> "%OUTPUT_FILE%"
echo Collection completed >> "%OUTPUT_FILE%"
echo Total files processed: %FILE_COUNT% >> "%OUTPUT_FILE%"
echo ======================================== >> "%OUTPUT_FILE%"

echo.
echo Collection completed!
echo Total files processed: %FILE_COUNT%
echo Output saved to: %OUTPUT_FILE%

REM Show file size
for %%A in ("%OUTPUT_FILE%") do echo Output file size: %%~zA bytes

REM Cleanup
if exist "%ALL_FILES_TEMP%" del "%ALL_FILES_TEMP%"

pause