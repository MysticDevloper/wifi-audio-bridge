cmake_minimum_required(VERSION 3.22)
project("audiobridge" C)

add_library(audiobridge SHARED
    audio_engine.c
    audio_bridge_jni.c
)

target_include_directories(audiobridge PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

find_library(log-lib log)
target_link_libraries(audiobridge aaudio ${log-lib})
