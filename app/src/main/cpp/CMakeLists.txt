# ═════════════════════════════════════════════════════════════════════════
# Janadhikar native layer — two independent shared libraries:
#
#   1. libjanadhikar_whisper.so   → whisper.cpp + JNI bridge (STT)
#   2. libjanadhikar_sqlitevec.so → SQLite amalgamation + sqlite-vec + JNI
#
# Vendored sources (git submodules — see setup instructions at bottom):
#   external/whisper.cpp     https://github.com/ggml-org/whisper.cpp
#   external/sqlite-vec      https://github.com/asg017/sqlite-vec
#   external/sqlite-amalgamation  (sqlite.org amalgamation zip, checked in)
# ═════════════════════════════════════════════════════════════════════════
cmake_minimum_required(VERSION 3.22.1)
project(janadhikar_native LANGUAGES C CXX)

set(EXTERNAL_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# ── 0. llama.cpp (on-device LLM) ──────────────────────────────────────────
# Added BEFORE whisper.cpp so llama's ggml defines the shared `ggml` target;
# whisper.cpp then reuses it (its CMake guards `if (NOT TARGET ggml)`), so the
# two ggml-based libraries coexist without a duplicate-target collision.
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
add_subdirectory(${EXTERNAL_DIR}/llama.cpp llama_build)

add_library(janadhikar_llama SHARED llama/llama_jni.cpp)
target_include_directories(janadhikar_llama PRIVATE ${EXTERNAL_DIR}/llama.cpp/include)
target_link_libraries(janadhikar_llama llama android log)
target_link_options(janadhikar_llama PRIVATE "-Wl,-z,max-page-size=16384")

# ── 1. whisper.cpp ────────────────────────────────────────────────────────
# whisper.cpp ships its own CMake; we consume it as a static lib and wrap
# it behind our JNI surface so no ggml symbols leak.
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
add_subdirectory(${EXTERNAL_DIR}/whisper.cpp whisper_build)

add_library(janadhikar_whisper SHARED
    whisper/whisper_jni.cpp
)
target_link_libraries(janadhikar_whisper
    whisper
    android
    log
)
# Android 15+ requires 16 KB page-size-aligned LOAD segments.
target_link_options(janadhikar_whisper PRIVATE "-Wl,-z,max-page-size=16384")

# ── 2. SQLite + sqlite-vec, statically fused ─────────────────────────────
# sqlite-vec.h is generated from sqlite-vec.h.tmpl (upstream does this in its
# Makefile via envsubst). We reproduce it here with configure_file so a fresh
# submodule checkout always builds.
file(READ ${EXTERNAL_DIR}/sqlite-vec/VERSION SQLITE_VEC_VERSION_RAW)
string(STRIP "${SQLITE_VEC_VERSION_RAW}" VERSION)
string(REGEX MATCH "^([0-9]+)\\.([0-9]+)\\.([0-9]+)" _ "${VERSION}")
set(VERSION_MAJOR ${CMAKE_MATCH_1})
set(VERSION_MINOR ${CMAKE_MATCH_2})
set(VERSION_PATCH ${CMAKE_MATCH_3})
set(DATE "vendored-build")
set(SOURCE "git-submodule")
configure_file(
    ${EXTERNAL_DIR}/sqlite-vec/sqlite-vec.h.tmpl
    ${CMAKE_CURRENT_BINARY_DIR}/generated/sqlite-vec.h
)

# We compile our OWN sqlite3 (not the Android system one) so that:
#   a) sqlite-vec can be statically linked — no runtime extension loading,
#      which is blocked/fragile on Android;
#   b) the knowledge DB behaves identically on every device and OS version.
add_library(janadhikar_sqlitevec SHARED
    sqlitevec/sqlite_vec_jni.cpp
    ${EXTERNAL_DIR}/sqlite-amalgamation/sqlite3.c
    ${EXTERNAL_DIR}/sqlite-vec/sqlite-vec.c
)
target_include_directories(janadhikar_sqlitevec PRIVATE
    ${CMAKE_CURRENT_BINARY_DIR}/generated
    ${EXTERNAL_DIR}/sqlite-amalgamation
    ${EXTERNAL_DIR}/sqlite-vec
)
target_compile_definitions(janadhikar_sqlitevec PRIVATE
    # Read-only, single-connection knowledge base → trim SQLite to the bone.
    SQLITE_ENABLE_STAT4
    SQLITE_ENABLE_FTS5             # keyword index for the hybrid retriever
    SQLITE_OMIT_LOAD_EXTENSION      # extensions are statically linked, not loaded
    SQLITE_DQS=0
    SQLITE_THREADSAFE=1
    SQLITE_DEFAULT_MEMSTATUS=0
    SQLITE_CORE                     # sqlite-vec builds as a core extension
)
target_link_libraries(janadhikar_sqlitevec
    android
    log
)
target_link_options(janadhikar_sqlitevec PRIVATE "-Wl,-z,max-page-size=16384")

# ═════════════════════════════════════════════════════════════════════════
# One-time submodule setup:
#   git submodule add https://github.com/ggml-org/whisper.cpp external/whisper.cpp
#   git submodule add https://github.com/asg017/sqlite-vec    external/sqlite-vec
#   # sqlite amalgamation: download from sqlite.org/download.html and place
#   # sqlite3.c / sqlite3.h under external/sqlite-amalgamation/
# ═════════════════════════════════════════════════════════════════════════
