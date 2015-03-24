default: all
#$(info core.mk)

ARDDUDE_DIR := $(dir $(lastword ${MAKEFILE_LIST}))..
include ${ARDDUDE_DIR}/etc/tools.mk

ARDDUDE_DIR := $(call truepath,${ARDDUDE_DIR})
TARGET_DIR := ${ARDDUDE_DIR}/target/${TARGET_BOARD}

include ${ARDDUDE_DIR}/etc/common.mk

SOURCE_DIRS := ${CORE_DIR} ${VARIANT_DIR}
ifneq ($(findstring arduino_due,${TARGET_BOARD}),)
SOURCE_DIRS += ${ARDUINO_IDE}/hardware/arduino/sam/system/libsam
CFLAGS_EXTRA := -std=c11 -I${ARDUINO_IDE}/hardware/arduino/sam/system/libsam/include
endif
ALL_SOURCES := $(foreach d,${SOURCE_DIRS},$(call rwildcard,$d,*.c) $(call rwildcard,$d,*.cpp))
OBJS := $(addsuffix .o,$(basename ${ALL_SOURCES:${HARDWARE_DIR}/%=${TARGET_DIR}/%}))

#$(info core : CORE_LIB=${CORE_LIB} in ${CORE_LIB_DIR})
$(info core : TARGET_DIR=${TARGET_DIR})
$(info core : OBJS=${OBJS})
all: ${TARGET_DIR}/libCore.a | ${TARGET_DIR}

${TARGET_DIR}/libCore.a: ${OBJS}

$(info HARDWARE_DIR=${HARDWARE_DIR})

vpath %.cpp ${HARDWARE_DIR}
vpath %.c   ${HARDWARE_DIR}
vpath %.S   ${HARDWARE_DIR}
