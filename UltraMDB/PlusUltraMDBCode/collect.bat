@echo off
setlocal enabledelayedexpansion
REM ========================================
REM Frontend Project File Collector (Quick Fix)
REM QUICK FIX: Use specific directory targeting instead of full recursion
REM ========================================

REM Configuration - modify these variables as needed
set "PROJECT_DIR=%~1"
set "OUTPUT_FILE=%~2"

REM Default values if parameters not provided
if "%PROJECT_DIR%"=="" set "PROJECT_DIR=."
if "%OUTPUT_FILE%"=="" set "OUTPUT_FILE=frontend_project_context.txt"

REM Convert to absolute paths
pushd "%PROJECT_DIR%" 2>nul
if errorlevel 1 (
    echo Error: Directory "%PROJECT_DIR%" does not exist
    exit /b 1
)
set "PROJECT_DIR=%CD%"
popd

echo Starting frontend project file collection...
echo Project directory: %PROJECT_DIR%
echo Output file: %OUTPUT_FILE%
echo.

REM Clear output file and add header
echo. > "%OUTPUT_FILE%"
set "FILE_COUNT=0"

REM Create temporary file for all files
set "ALL_FILES_TEMP=%TEMP%\frontend_collector_%RANDOM%.tmp"

REM QUICK FIX: Instead of using /s (recursive), target specific common directories
echo Collecting files from common frontend directories...

REM Common frontend source directories (adjust as needed for your project structure)
set "SEARCH_DIRS=src components pages app lib utils hooks styles public assets"

REM Search in root directory first
echo Searching root directory...
pushd "%PROJECT_DIR%"

REM Root level files (excluding package-lock.json)
for %%E in (*.tsx *.ts *.jsx *.js *.mjs *.cjs *.html *.htm *.css *.scss *.sass *.less *.vue *.svelte *.md *.yaml *.yml) do (
    if exist "%%E" echo %PROJECT_DIR%\%%E >> "%ALL_FILES_TEMP%"
)

REM Config files (root only) - excluding package-lock.json
for %%C in (package.json tsconfig*.json *.config.js *.config.ts *.config.mjs *.config.cjs .env* .gitignore .eslintrc* .prettierrc* .babelrc* Dockerfile* docker-compose.* jest.config.* vitest.config.*) do (
    if exist "%%C" (
        REM Skip package-lock.json specifically
        echo %%C | findstr /I "package-lock.json" >nul 2>&1
        if !errorlevel! neq 0 (
            echo %PROJECT_DIR%\%%C >> "%ALL_FILES_TEMP%"
        )
    )
)

popd

REM Search in common source directories (recursively, but limited scope)
for %%D in (%SEARCH_DIRS%) do (
    if exist "%PROJECT_DIR%\%%D" (
        echo Searching %%D directory...
        
        REM React/TypeScript files
        dir /s /b "%PROJECT_DIR%\%%D\*.tsx" 2>nul >> "%ALL_FILES_TEMP%"
        dir /s /b "%PROJECT_DIR%\%%D\*.ts" 2>nul >> "%ALL_FILES_TEMP%"
        dir /s /b "%PROJECT_DIR%\%%D\*.jsx" 2>nul >> "%ALL_FILES_TEMP%"
        dir /s /b "%PROJECT_DIR%\%%D\*.js" 2>nul >> "%ALL_FILES_TEMP%"
        dir /s /b "%PROJECT_DIR%\%%D\*.mjs" 2>nul >> "%ALL_FILES_TEMP%"
        dir /s /b "%PROJECT_DIR%\%%D\*.cjs" 2>nul >> "%ALL_FILES_TEMP%"
        
        REM HTML/CSS files
        dir /s /b "%PROJECT_DIR%\%%D\*.html" 2>nul >> "%ALL_FILES_TEMP%"
        dir /s /b "%PROJECT_DIR%\%%D\*.htm" 2>nul >> "%ALL_FILES_TEMP%"
        dir /s /b "%PROJECT_DIR%\%%D\*.css" 2>nul >> "%ALL_FILES_TEMP%"
        dir /s /b "%PROJECT_DIR%\%%D\*.scss" 2>nul >> "%ALL_FILES_TEMP%"
        dir /s /b "%PROJECT_DIR%\%%D\*.sass" 2>nul >> "%ALL_FILES_TEMP%"
        dir /s /b "%PROJECT_DIR%\%%D\*.less" 2>nul >> "%ALL_FILES_TEMP%"
        dir /s /b "%PROJECT_DIR%\%%D\*.styl" 2>nul >> "%ALL_FILES_TEMP%"
        
        REM Vue/Svelte files
        dir /s /b "%PROJECT_DIR%\%%D\*.vue" 2>nul >> "%ALL_FILES_TEMP%"
        dir /s /b "%PROJECT_DIR%\%%D\*.svelte" 2>nul >> "%ALL_FILES_TEMP%"
        
        REM Documentation and other formats
        dir /s /b "%PROJECT_DIR%\%%D\*.md" 2>nul >> "%ALL_FILES_TEMP%"
        dir /s /b "%PROJECT_DIR%\%%D\*.json" 2>nul >> "%ALL_FILES_TEMP%"
        
        REM Testing files
        dir /s /b "%PROJECT_DIR%\%%D\*.test.js" 2>nul >> "%ALL_FILES_TEMP%"
        dir /s /b "%PROJECT_DIR%\%%D\*.test.ts" 2>nul >> "%ALL_FILES_TEMP%"
        dir /s /b "%PROJECT_DIR%\%%D\*.test.tsx" 2>nul >> "%ALL_FILES_TEMP%"
        dir /s /b "%PROJECT_DIR%\%%D\*.spec.js" 2>nul >> "%ALL_FILES_TEMP%"
        dir /s /b "%PROJECT_DIR%\%%D\*.spec.ts" 2>nul >> "%ALL_FILES_TEMP%"
        dir /s /b "%PROJECT_DIR%\%%D\*.spec.tsx" 2>nul >> "%ALL_FILES_TEMP%"
    )
)

REM If you have other specific directories, add them here
REM Example: if exist "%PROJECT_DIR%\tests" dir /s /b "%PROJECT_DIR%\tests\*.test.*" 2>nul >> "%ALL_FILES_TEMP%"

REM Sort and remove duplicates
if exist "%ALL_FILES_TEMP%" (
    sort /unique "%ALL_FILES_TEMP%" > "%ALL_FILES_TEMP%.sorted"
    move "%ALL_FILES_TEMP%.sorted" "%ALL_FILES_TEMP%" >nul 2>&1
) else (
    echo No files found.
    goto :cleanup
)

REM Process each file (same as original, but exclude package-lock.json)
echo Processing files...
for /f "usebackq delims=" %%F in ("%ALL_FILES_TEMP%") do (
    if exist "%%F" (
        REM Skip package-lock.json files
        echo %%F | findstr /I "package-lock.json" >nul 2>&1
        if !errorlevel! neq 0 (
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
        ) else (
            echo Skipping: package-lock.json
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

:cleanup
REM Cleanup
if exist "%ALL_FILES_TEMP%" del "%ALL_FILES_TEMP%"

pause