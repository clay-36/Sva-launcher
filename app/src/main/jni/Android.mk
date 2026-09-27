LOCAL_PATH := $(call my-dir)
DUO_JNI_PATH := $(LOCAL_PATH)

include $(DUO_JNI_PATH)/../../../../third_party/nestopia/libretro/jni/Android.mk

LOCAL_PATH := $(DUO_JNI_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := duo_nes
LOCAL_SRC_FILES := duo_nes.cpp
LOCAL_C_INCLUDES := $(DUO_JNI_PATH)/../../../../third_party/nestopia/libretro/libretro-common/include
LOCAL_CPPFLAGS := -std=c++17 -Wall -Wextra
LOCAL_LDLIBS := -landroid -llog -ldl -laaudio
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
include $(BUILD_SHARED_LIBRARY)
