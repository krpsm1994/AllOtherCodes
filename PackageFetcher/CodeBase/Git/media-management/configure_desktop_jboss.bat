@echo off

REM Script for configuring JBoss on desktop development environment
REM See secion 3.3 of the Artesia Development Environment Guide
REM
REM Since:  7.0
REM Author: Josh Nan

if "%1" == "oracle11" GOTO ORACLE11
if "%1" == "sqlserver" GOTO SQL_SERVER
GOTO USAGE

:ORACLE11
@echo Copying Oracle JDBC Driver...
del %JBOSS_HOME%\server\teams\lib\ojdbc14.jar
del %JBOSS_HOME%\server\teams\lib\ojdbc5.jar
copy %ORACLE_HOME%\jdbc\lib\ojdbc8.jar %JBOSS_HOME%\server\teams\lib
set dbdir=oracle
GOTO JBOSS_CONFIG

:SQL_SERVER
@echo Copying SqlServer JDBC Driver...
set dbdir=sqlserver
copy %SOURCE_ROOT%\lib\deploy\mssql-jdbc-13.2.1.jre11.jar %JBOSS_HOME%\server\teams\lib
GOTO JBOSS_CONFIG

:JBOSS_CONFIG
@echo Copying JBoss Configuration files...
xcopy /s /y /q %SOURCE_ROOT%\server\conf\jboss\prod\base %JBOSS_HOME%
xcopy /s /y /q %SOURCE_ROOT%\server\conf\jboss\prod\windows %JBOSS_HOME%
xcopy /s /y /q %SOURCE_ROOT%\server\conf\jboss\prod\%dbdir% %JBOSS_HOME%
xcopy /s /y /q %SOURCE_ROOT%\server\conf\jboss\dev\base %JBOSS_HOME%
xcopy /s /y /q %SOURCE_ROOT%\server\conf\jboss\dev\%dbdir% %JBOSS_HOME%

echo.
echo * 
echo * NOTE: You must perform these steps manually
echo * 
echo 1. Edit the JDBC URL connection strings in the %JBOSS_HOME%\server\teams\deploy\teamsdb-ds.xml file to point to the DB of the development server.
echo.     
echo 2. Edit your %TEAMS_HOME%\data\cs\global\Tresource file. Make sure the [JNDI\CONFIG\PROVIDER_HOST] setting points to your local JBoss. The value is typically localhost:11099
echo.   
goto END

:USAGE
echo.
echo Usage
echo -----
echo   To configure desktop env for Oracle10:    configure_desktop_jboss oracle10 
echo   To configure desktop env for Oracle11:    configure_desktop_jboss oracle11
echo   To configure desktop env for SqlServer:   configure_desktop_jboss sqlserver
echo.

:END