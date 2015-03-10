ifeq (${MAKEFILE_TOOLS_INCLUDED},)
  include ${ARDDUDE_DIR}/etc/tools.mk
endif

# default current dir to here
CALLER_DIR := $(call truepath,$(dir $(firstword ${MAKEFILE_LIST})))
#$(info target.mk : $(dir $(firstword ${MAKEFILE_LIST})) -> CALLER_DIR = ${CALLER_DIR})

## deduce target dir
TARGET_DIR ?= ${CALLER_DIR}/target/${TARGET_BOARD}
#$(info target.mk : TARGET_DIR = ${TARGET_DIR})
export MKDIR ?= mkdir -p

MAKE_MAKE = ${ARDDUDE_DIR}/etc/mkmk.${BATCH_EXT} -I ${ARDUINO_IDE} ${MAKE_MAKE_FLAGS}
# --debug
ARD_CONSOLE = ${ARDDUDE_DIR}/etc/console.${BATCH_EXT} -I ${ARDUINO_IDE} ${ARD_CONSOLE_FLAGS}
# --debug

%/target/${TARGET_BOARD} :
	-${MKDIR} $@

## look for target specific Makefile, or generate it
TARGET_MAKEFILE := ${ARDDUDE_DIR}/target/${TARGET_BOARD}/Makefile
${TARGET_MAKEFILE} : | ${ARDDUDE_DIR}/target/${TARGET_BOARD}
	${MAKE_MAKE} --board ${TARGET_BOARD} --output ${TARGET_MAKEFILE}

-include ${TARGET_MAKEFILE}

ifneq (${ARCH},unix)
  # have to cd into Arduino IDE directory to let binaries find cygwin dll
  BIN_PREFIX := cd ${ARDUINO_IDE} ;
endif

MAKEFILE_TARGET_INCLUDED := true
#$(info target.mk : TARGET_PLATFORM = ${TARGET_PLATFORM})
