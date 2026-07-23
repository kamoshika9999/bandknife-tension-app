@echo off
setlocal EnableDelayedExpansion

set "RESOLVED="

@rem Explicit override (highest priority).
if defined GRADLE_JAVA_HOME (
  if exist "%GRADLE_JAVA_HOME%\bin\java.exe" (
    set "RESOLVED=%GRADLE_JAVA_HOME%"
    goto publish
  )
)

@rem Optional per-machine setting in local.properties:
@rem   java.home=C\:\\Program Files\\Eclipse Adoptium\\jdk-21.0.11.10-hotspot
set "LOCAL_PROPS=%~dp0local.properties"
if exist "%LOCAL_PROPS%" (
  for /f "usebackq tokens=1,* delims==" %%a in (`findstr /b /c:"java.home=" "%LOCAL_PROPS%"`) do (
    set "RAW=%%b"
    set "RAW=!RAW:\:=!"
    set "RAW=!RAW:\\=\!"
    if exist "!RAW!\bin\java.exe" (
      set "RESOLVED=!RAW!"
      goto publish
    )
  )
)

@rem Keep JAVA_HOME when it looks like JDK 21 or lower.
set "NEED_FALLBACK=0"
if not defined JAVA_HOME set "NEED_FALLBACK=1"
if defined JAVA_HOME (
  echo %JAVA_HOME% | findstr /I /R "jdk-2[2-9] jdk-3" >nul && set "NEED_FALLBACK=1"
)
if "%NEED_FALLBACK%"=="0" (
  if exist "%JAVA_HOME%\bin\java.exe" (
    endlocal & exit /b 0
  )
  set "NEED_FALLBACK=1"
)

@rem JAVA_HOME is missing or too new; search common JDK locations.
for /d %%j in (
  "C:\Program Files\Eclipse Adoptium\jdk-21*"
  "C:\Program Files\Eclipse Adoptium\jdk-17*"
  "C:\Program Files\Java\jdk-21*"
  "C:\Program Files\Java\jdk-17*"
  "C:\Program Files\Android\Android Studio\jbr"
  "C:\Program Files\Android\Android Studio\jre"
) do (
  if exist "%%j\bin\java.exe" (
    set "RESOLVED=%%j"
    goto publish
  )
)

@rem No compatible JDK found; keep the original JAVA_HOME and let Gradle report the error.
endlocal & exit /b 0

:publish
if not defined RESOLVED (
  endlocal & exit /b 0
)
set "TARGET=!RESOLVED!"
endlocal & set "GRADLE_RESOLVED_JAVA_HOME=%TARGET%"
exit /b 0
