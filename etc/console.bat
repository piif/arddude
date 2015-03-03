@echo off

REM minimal bat launcher for ArdConsole class

set ROOTDIR=%~dp0..

for %%j IN (%ROOTDIR%\target\Ard*-shaded.jar %ROOTDIR%\lib\arddude*.jar) DO SET JAR=%%j
java -cp %JAR% pif.arduino.ArdConsole %*