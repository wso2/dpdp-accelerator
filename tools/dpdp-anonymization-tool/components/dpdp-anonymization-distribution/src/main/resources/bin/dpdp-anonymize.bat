@echo off
setlocal
set "TOOL_HOME=%~dp0.."
java -jar "%TOOL_HOME%\lib\dpdp-anonymization-tool.jar" --config "%TOOL_HOME%\conf\config.json" %*
exit /b %ERRORLEVEL%
