cmake_minimum_required(VERSION 3.22)
project(pyrite C)

# PYTHON_PREFIX_DIR contains a {{triplet}} placeholder (AGP reserves ${} for itself);
# the NDK toolchain sets CMAKE_LIBRARY_ARCHITECTURE to the triplet of the ABI being built.
string(REPLACE "{{triplet}}" "${CMAKE_LIBRARY_ARCHITECTURE}" PYTHON_PREFIX_DIR "${PYTHON_PREFIX_DIR}")

add_library(pyrite SHARED pyrite.c)
target_include_directories(pyrite PRIVATE "${PYTHON_PREFIX_DIR}/include/python${PYTHON_VERSION}")
target_link_directories(pyrite PRIVATE "${PYTHON_PREFIX_DIR}/lib")
target_link_libraries(pyrite PRIVATE log "python${PYTHON_VERSION}")
target_compile_options(pyrite PRIVATE -Wall -Wextra -Wno-unused-parameter)
