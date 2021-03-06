default: console
#$(info main.mk)

ARDDUDE_DIR := $(dir $(lastword ${MAKEFILE_LIST}))..
ifeq (${MAKEFILE_TOOLS_INCLUDED},)
  include ${ARDDUDE_DIR}/etc/tools.mk
endif
#$(info ARCH=${ARCH})

## all *_DIR variables must contain absolute path names without trailing /

## MAKEFILE_LIST 1st item is first caller Makefile => deduce caller project dir
CALLER_DIR := $(call truepath,$(dir $(firstword ${MAKEFILE_LIST})))
#$(info main : CALLER_DIR=${CALLER_DIR})

include ${ARDDUDE_DIR}/etc/target.mk

## PROJECT_NAME may be already defined, else, use project dirname
PROJECT_NAME ?= $(notdir ${CALLER_DIR})

SOURCE_EXCLUDES += target/

#$(info main start : SOURCE_DIRS=${SOURCE_DIRS})
ifeq (${SOURCE_DIRS},)
  SOURCE_DIRS := $(call rsubdirs,${CALLER_DIR})
else
  #$(info $(foreach d,${SOURCE_DIRS},$(call truepath,$d)))
  override SOURCE_ROOTDIRS := $(foreach d,${SOURCE_DIRS},$(call truepath,$d))
  override SOURCE_DIRS := $(foreach d,${SOURCE_ROOTDIRS},$(call rsubdirs,$d))
endif
#$(info main after truepath: SOURCE_DIRS=${SOURCE_DIRS})

ifneq (${SOURCE_EXCLUDES},)
  override SOURCE_EXCLUDES := $(addsuffix /%,$(foreach d,${SOURCE_EXCLUDES},$(call truepath,$d)))
  override SOURCE_DIRS := $(filter-out ${SOURCE_EXCLUDES},${SOURCE_DIRS})
  #$(info main after excludes ${SOURCE_EXCLUDES} : SOURCE_DIRS=${SOURCE_DIRS})
endif
$(info main : SOURCE_DIRS=${SOURCE_DIRS})

## by default, look for a main source file with project name or "main" as basename 
## but user may define SOURCES_DIR=libraries/ + MAIN_SOURCE=subProject/subProject.ino
ifeq (${MAIN_SOURCE},)
#  MAIN_SOURCE = $(wildcard \
#		${CALLER_DIR}/${PROJECT_NAME}.ino ${CALLER_DIR}/main.ino \
#		${CALLER_DIR}/${PROJECT_NAME}.cpp ${CALLER_DIR}/main.cpp \
#		${CALLER_DIR}/${PROJECT_NAME}.c ${CALLER_DIR}/main.c)
  OUT_NAME = ${PROJECT_NAME}
else
  OUT_NAME = $(notdir $(basename ${MAIN_SOURCE}))
  override MAIN_SOURCE := ${CALLER_DIR}/${MAIN_SOURCE}
endif

# call $sort to avoid duplicates between MAIN_SOURCE and found sources
ALL_SOURCES := $(foreach d,${SOURCE_DIRS},$(call wildcards,$d,*.c *.cpp *.ino))
ifneq (${SOURCE_EXCLUDE_PATTERNS},)
  ALL_SOURCES := $(call filter-out-substr,${SOURCE_EXCLUDE_PATTERNS},${ALL_SOURCES})
  #$(info main after exclude patterns ${SOURCE_EXCLUDE_PATTERNS}: ALL_SOURCES=${ALL_SOURCES})
endif
ALL_SOURCES := $(sort ${MAIN_SOURCE} ${ALL_SOURCES})

EXTERNAL_SOURCE_DIRS := $(filter-out ${CALLER_DIR}/%,${SOURCE_ROOTDIRS}))
vpath %.c ${EXTERNAL_SOURCE_DIRS}
vpath %.cpp ${EXTERNAL_SOURCE_DIRS}

# deduce all .o files, with path names relative to target dir
ABS_OBJS := $(addsuffix .o,$(basename ${ALL_SOURCES}))
OBJS := $(foreach root,${CALLER_DIR} ${EXTERNAL_SOURCE_DIRS},$(subst ${root},${TARGET_DIR},$(filter ${root}/%,${ABS_OBJS})))
#$(info main OBJS=${OBJS})
## ok .. basic variables are defined, we can call common makefile

## now, main target dir and core libs one are known

objects: ${OBJS}
include ${ARDDUDE_DIR}/etc/common.mk

.SECONDARY: ${OBJS}

# add PIF_TOOL_CHAIN define
CFLAGS_EXTRA += -DPIF_TOOL_CHAIN
CXXFLAGS_EXTRA += -DPIF_TOOL_CHAIN

$(info main : TARGET_DIR=${TARGET_DIR})

## deduce out file name
ifeq (${TODO},lib)
  OUT_PATH := ${TARGET_DIR}/lib${OUT_NAME}.a
else
  OUT_PATH := ${TARGET_DIR}/${OUT_NAME}${UPLOAD_EXT}
endif

$(info main : OUT_PATH=${OUT_PATH})

all: ${OUT_PATH} | ${TARGET_DIR}
#TODO size
bin: ${OUT_PATH} | ${TARGET_DIR}
lib: ${OUT_PATH} | ${TARGET_DIR}

LOCAL_CORE_LIB := ${TARGET_DIR}/${CORE_LIB_NAME}
${LOCAL_CORE_LIB}: ${CORE_LIB} | ${TARGET_DIR}
	cp $< $@

export ARDUINO_IDE
${CORE_LIB}:
	${MAKE} -C ${ARDDUDE_DIR} -f etc/core.mk

${OUT_PATH}: ${LOCAL_CORE_LIB} ${OBJS} ${DEPENDENCIES}
#${OUT_PATH}: ${CORE_LIB} ${OBJS} ${DEPENDENCIES}

# try to launch upload only if binary got compiled again
DO_UPLOAD=""
console: ${TARGET_DIR}/consoleFlag
	$(if ${UPLOAD_PORT},,$(error UPLOAD_PORT must be specified))
	${ARD_CONSOLE} -b ${TARGET_BOARD} -p ${UPLOAD_PORT} -f ${OUT_PATH} ${UPLOAD_OPTIONS} ${DO_UPLOAD}

${TARGET_DIR}/consoleFlag: ${OUT_PATH}
	touch ${TARGET_DIR}/consoleFlag
	$(eval DO_UPLOAD="-u")

# always launch upload, even if binary didn't get compiled
upload: ${OUT_PATH}
	$(if ${UPLOAD_PORT},,$(error UPLOAD_PORT must be specified))
	${ARD_CONSOLE} -b ${TARGET_BOARD} -p ${UPLOAD_PORT} -f ${OUT_PATH} -u -x

.PHONY: bin lib dependencies corelib upload console discovery

# rules to let eclipse discover constants and includes
ifeq (${MAKECMDGOALS},discovery)

  #$(info CMD='${CMD}')
  CMD_LAST := $(lastword ${CMD})
  #$(info CMD_LAST=${CMD_LAST})
  CMD_FIRST := $(firstword ${CMD})
  #$(info CMD_FIRST=${CMD_FIRST})
  CMD_REMAIN := $(wordlist 2,1000,$(filter-out $(lastword ${CMD}),${CMD}))
  #$(info CMD_REMAIN=${CMD_REMAIN})

  ifeq (${CMD_FIRST},gcc)
    DISCOVERY_CMD = ${CC}
    DISCOVERY_FLAGS = ${DISCOVERY_FLAGS_GCC} ${CFLAGS_EXTRA}
  else ifeq (${CMD_FIRST},g++)
    DISCOVERY_CMD = ${CXX}
    DISCOVERY_FLAGS = ${DISCOVERY_FLAGS_GXX} ${CXXFLAGS_EXTRA}
  else
    $(error unexpected command ${CMD_FISRT})
  endif

#  ifeq (${ARCH},cygwin)
#    DISCOVERY_FILTER := 2>&1|sed 's|/cygdrive/\(.\)|\1:|g'
#  endif
endif

discovery:
	${BIN_PREFIX} ${DISCOVERY_CMD} ${CMD_REMAIN} ${DISCOVERY_FLAGS} ${INCLUDE_FLAGS} ${INCLUDE_FLAGS_EXTRA} ${CMD_LAST} ${DISCOVERY_FILTER}

clean:
	rm -rf ${TARGET_DIR}


## gory details about some compilation hacks included in arduino recipes

ifneq ($(findstring arduino_due,${TARGET_BOARD}),)
# linking needs one of .o core files in its command line => copy it with attended name
${OUT_PATH}: ${TARGET_DIR}/syscalls_sam3.c.o
${TARGET_DIR}/syscalls_sam3.c.o: ${CORE_LIB_DIR}/sam/cores/arduino/syscalls_sam3.o | ${TARGET_DIR}
	cp $< $@
endif
