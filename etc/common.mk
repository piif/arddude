#$(info common.mk)

## verify that ARDUINO_IDE and TARGET_BOARD are defined
ifeq (${TARGET_BOARD},)
  $(error TARGET_BOARD is not defined)
endif

ifeq (${ARDUINO_IDE},)
  ifneq ($(wildcard ${ARDDUDE_DIR}/target/${TARGET_BOARD}/Makefile),)
    $(info *** ARDUINO_IDE not defined, will use value from target makefile ***)
  else
    ifeq ($(OS),Windows_NT)
      ARDUINO_IDE := $(wildcard ${ProgramFiles(x86)}/arduino)
    else
      ARDUINO_IDE := $(lastword $(sort $(wildcard /usr/share/arduino /usr/local/arduino-* /opt/arduino-*)))
    endif
    ifeq (${ARDUINO_IDE},)
      $(error ARDUINO_IDE is not defined)
    else
      $(warning *** ARDUINO_IDE not defined, using auto discovered value ${ARDUINO_IDE} ***)
    endif
  endif
endif

ifeq (${MAKEFILE_TARGET_INCLUDED},)
  include ${ARDDUDE_DIR}/etc/target.mk
endif

## and core lib
#CORE_LIB_DIR := ${TARGET_DIR}
CORE_LIB_DIR := $(call truepath,${ARDDUDE_DIR}/target/${TARGET_BOARD})
#VPATH += ${CORE_LIB_DIR}
CORE_LIB_NAME := libCore.a
CORE_LIB := ${CORE_LIB_DIR}/${CORE_LIB_NAME}
