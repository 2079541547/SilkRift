cmake_minimum_required(VERSION 3.22.1)

project("silkrift")

add_subdirectory(Dobby)

add_library(silkrift SHARED
        silkrift.cpp
        src/IORedirects.cpp
        src/SysCallHook.cpp
        src/MapsGhost.cpp
        src/StealthCrashBypass.cpp
        src/LinkerBypass.cpp
)

target_include_directories(silkrift
        PUBLIC
        includes
)

target_link_libraries(silkrift
        dobby_static
        android
        log)