cmake_minimum_required(VERSION 3.22.1)

project("ytpro")

add_library(
        ytpro-native
        SHARED
        native-lib.cpp
)

find_library(
        log-lib
        log
)

target_link_libraries(
        ytpro-native
        ${log-lib}
)
