echo off

rem Compile the engine report java code and build the 
rem enginereport.jar

if not "%JAVA_HOME%" == "" goto SET_JAVAC

echo JAVA_HOME is not set.
echo Set JAVA_HOME to the directory of your local JDK.
goto END

:SET_JAVAC
set JAVAC=%JAVA_HOME%\bin\javac.exe
set JAR=%JAVA_HOME%\bin\jar.exe

:END

rem Create the output directories
mkdir c:\pinnacle\staging\enginereport

%JAVAC% -classpath c:\pinnacle\java\jars\Regexp.jar;c:\pinnacle\staging\server\jars\TEAMS.jar -d c:\pinnacle\staging\enginereport c:\pinnacle\java\common\src\ttsg_teams\common\common\LogMessage.java c:\pinnacle\java\common\src\ttsg_teams\common\common\ReportWriter.java c:\pinnacle\java\common\src\ttsg_teams\common\common\EngineReport.java

cd c:\pinnacle\staging\enginereport

%JAR% -cvf c:\pinnacle\staging\server\jars\enginereport.jar ttsg_teams
