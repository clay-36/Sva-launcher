LOCAL_PATH := $(call my-dir)

CORE_DIR = $(LOCAL_PATH)/../..

include $(CORE_DIR)/libretro/Makefile.common

COREFLAGS := -DANDROID -D__LIBRETRO__ $(INCFLAGS) -Wno-c++11-narrowing \
             -Wno-unknown-escape-sequence -Wno-invalid-source-encoding

GIT_VERSION := " $(shell git rev-parse --short HEAD || echo unknown)"
ifneq ($(GIT_VERSION)," unknown")
	COREFLAGS += -DGIT_VERSION=\"$(GIT_VERSION)\"
endif

include $(CLEAR_VARS)
LOCAL_MODULE       := retro
# Keep object/dependency paths short and reproducible when the core is vendored
# below another Android project. The upstream lists are absolute via CORE_DIR.
LOCAL_SRC_FILES    := $(patsubst $(CORE_DIR)/%,../../%,$(SOURCES_CXX) $(SOURCES_C))
LOCAL_CFLAGS       := $(COREFLAGS)
LOCAL_CXXFLAGS     := $(COREFLAGS)
LOCAL_LDFLAGS      := -Wl,-version-script=$(CORE_DIR)/libretro/link.T
LOCAL_LDLIBS       := -lz
LOCAL_CPP_FEATURES := exceptions
include $(BUILD_SHARED_LIBRARY)
