cmake_minimum_required(VERSION 3.18.1)
project(cfc)
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -mllvm -inline-threshold=5000")

# OpenCV path kontrolü ve düzeltmesi
set(OPENCV_BASE_DIR "${CMAKE_SOURCE_DIR}/../opencv/sdk/native")
set(OPENCV_LIB_PATH "${OPENCV_BASE_DIR}/libs/${ANDROID_ABI}/libopencv_java4.so")

# OpenCV kütüphanesinin varlığını kontrol et
if(NOT EXISTS ${OPENCV_LIB_PATH})
    message(WARNING "OpenCV library not found at: ${OPENCV_LIB_PATH}")
    # Alternatif path'leri dene
    set(ALT_PATHS
        "${CMAKE_SOURCE_DIR}/../opencv/sdk/native/libs/${ANDROID_ABI}/libopencv_java4.so"
        "${CMAKE_SOURCE_DIR}/opencv/sdk/native/libs/${ANDROID_ABI}/libopencv_java4.so"
        "${OpenCV_DIR}/libs/${ANDROID_ABI}/libopencv_java4.so"
    )
    
    foreach(alt_path ${ALT_PATHS})
        if(EXISTS ${alt_path})
            set(OPENCV_LIB_PATH ${alt_path})
            message(STATUS "Found OpenCV at: ${OPENCV_LIB_PATH}")
            break()
        endif()
    endforeach()
endif()

# OpenCV kütüphanesini tanımla
add_library(lib_opencv SHARED IMPORTED)
set_target_properties(lib_opencv PROPERTIES IMPORTED_LOCATION ${OPENCV_LIB_PATH})

# Hata durumunda debug bilgisi
if(NOT EXISTS ${OPENCV_LIB_PATH})
    message(FATAL_ERROR "OpenCV library still not found! Checked path: ${OPENCV_LIB_PATH}")
endif()

set(DISABLE_TESTS true)
set(OPENCV_LIBS "lib_opencv")

# Include directories - daha esnek path yapısı
set(OPENCV_INCLUDE_DIRS
    "${OPENCV_BASE_DIR}/jni/include"
    "${OPENCV_BASE_DIR}/jni/include/opencv2"
    "${CMAKE_SOURCE_DIR}/../opencv/sdk/native/jni/include"
    "${CMAKE_SOURCE_DIR}/../opencv/sdk/native/jni/include/opencv2"
    "${OpenCV_DIR}/jni/include"
    "${OpenCV_DIR}/jni/include/opencv2"
)

# Sadece var olan include directory'leri ekle
foreach(include_dir ${OPENCV_INCLUDE_DIRS})
    if(EXISTS ${include_dir})
        include_directories(${include_dir})
        message(STATUS "Added include directory: ${include_dir}")
    endif()
endforeach()

# Diğer include directory'ler
include_directories(
    .
    libcimbar/src/lib
    libcimbar/src/third_party_lib
)

# Projeler
set(PROJECTS
   cfc-cpp
   concurrent
   libcimbar
)

foreach(proj ${PROJECTS})
    add_subdirectory(${proj} build/${proj})
endforeach()
