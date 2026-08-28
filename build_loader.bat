@echo off
call "D:\visual studio\VC\Auxiliary\Build\vcvarsall.bat" x64
set "PATH=D:\visual studio\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin;%PATH%"
cd /d "D:\clientmake\LionInjectable-1.0.5"
call build.bat
