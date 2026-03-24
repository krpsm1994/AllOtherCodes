set PROG_NAME=%0
set PROPERTY_FILE=%1

call %PROPERTY_FILE%

REM set third party packages directory
set PKGDIR=c:\pkgs

REM set third party header files
set INCLUDE=%INCLUDE%;%PKGDIR%\centera\sdk-2.1\include;%PKGDIR%\Virage\include;%PKGDIR%\sp;%PKGDIR%\Rogue;%PKGDIR%\vbroker\include;%PKGDIR%\expat\xmlparse;%PKGDIR%\videologger\include;%JAVA_HOME%\include;%JAVA_HOME%\include\win32

REM set third party library files
set LIB=%LIB%;%PKGDIR%\centera\sdk-2.1\lib;%PKGDIR%\sp\lib;%PKGDIR%\expat;%PKGDIR%\vbroker\lib;%PKGDIR%\Rogue\lib;%PKGDIR%\videologger\lib;%JAVA_HOME%\lib

set FINDTOOL=c:\bin\find

REM retrieve files from the baseline manually
rem cd %WORKSETDIR%
cd %BUILD_DIR%

REM copy cpp files to the source directory

mkdir projects\src
%FINDTOOL% server/cpp -name "*.c" -exec cp {} projects\src ;
%FINDTOOL% server/cpp -name "*.cpp" -exec cp {} projects\src ;
copy server\cpp\srvmgr_nt\src\srvmgr.ico projects\src
copy server\cpp\srvmgr_nt\src\srvmgr.rc projects\src
copy server\cpp\srvmgr_nt\src\srvmgr.rc2 projects\src
copy server\cpp\srvmgr_nt\src\context.ico projects\src

mkdir projects\include
xcopy /s /Y server\cpp\oncrpc_nt\include projects\include
xcopy /s /Y data\schema\include projects\include
%FINDTOOL% server/cpp -name "*.h" ! -regex ".*oncrpc_nt.*" -exec cp {} projects\include ;

REM Build
cd projects\teams
msdev teams.dsw /MAKE "Top - Win32 Release" /REBUILD /OUT testmsdev.log /USEENV

rem cd %WORKSETDIR%\build_scripts\windows
cd %BUILD_DIR%\build_scripts\windows
rem copy compiled binaries
rem call copybin strip %WORKSETDIR%\integnt\bin %WORKSETDIR%\integnt\bin
call copybin strip %BUILD_DIR%\integnt\bin %BUILD_DIR%\integnt\bin
