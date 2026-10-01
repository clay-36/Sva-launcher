LOCAL_PATH := $(call my-dir)
SVA_JNI_PATH := $(LOCAL_PATH)

include $(SVA_JNI_PATH)/../../../../third_party/nestopia/libretro/jni/Android.mk

LOCAL_PATH := $(SVA_JNI_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := sva_nes
LOCAL_SRC_FILES := sva_nes.cpp
LOCAL_C_INCLUDES := $(SVA_JNI_PATH)/../../../../third_party/nestopia/libretro/libretro-common/include
LOCAL_CPPFLAGS := -std=c++17 -Wall -Wextra
LOCAL_LDLIBS := -landroid -llog -ldl -laaudio
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
include $(BUILD_SHARED_LIBRARY)
