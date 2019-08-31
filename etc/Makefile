# TODO ...

MAKEFILE_DIR := $(patsubst %/,%, $(dir $(realpath ${MAKEFILE_LIST})))

# deduce some info from source directory
PROJECT_DIR := ${CURDIR}
$(info running from ${PROJECT_DIR})

PROJECT_NAME:= $(notdir ${PROJECT_DIR})
PROJECT_MAIN ?= ${PROJECT_NAME}
MAIN_SOURCE := ${PROJECT_MAIN}.ino

BUILD_PATH := ${PROJECT_DIR}/build

# allow user to add :
# - specific options to arduino-builder compile and upload commands
# - default values to BOARD, PORT and BAUDRATE (but command line may override them)
# - MAIN_SOURCE, BUILD_PATH (why not ?)
-include makefile.def

# deduce board and port if not defined from environment variables or command line
ifneq "" "$(wildcard ${BUILD_PATH}/sketch.json)"
    BOARD ?= $(shell jq -r .cpu.fqbn < ${BUILD_PATH}/sketch.json)
endif
ifeq "" "${BOARD}"
    $(error BOARD variable must be defined)
endif
BOARD_DOT := $(subst :,.,${BOARD})

PORT ?= $(shell arduino-cli board list --format json | jq --arg BOARD ${BOARD} -r \
  'map({ a:.address, f: .boards | map(.FQBN)[] | select(.==$$BOARD) }) | map(.a)[]' )

BAUDRATE ?= 115200

LIBRARY_PATH_OPTS = $(patsubst %,-libraries %,${LIBRARY_PATH})

ARDUINO_DATA ?= $(shell arduino-cli config dump --format text | grep arduino_data | cut -d ' ' -f 2)
HARDWARE ?= ${ARDUINO_DATA}/packages

$(info Using board=${BOARD} port=${PORT} baudrate=${BAUDRATE})

HEX_FILE := ${BUILD_PATH}/${PROJECT_MAIN}.hex

# rules

.PHONY: clean compile upload console

all: compile

clean:
	rm -rf ${BUILD_PATH}

${BUILD_PATH}:
	mkdir -p ${BUILD_PATH}

# TODO : builder rewrite hex file even when nothing was modified
# copy .hex file to another place (with DOT_BOARD name)
# write .hex check sum in a file and override target hex file only if modified
# => console won't upload ahex file if it's unmodified

compile: ${BUILD_PATH}
	arduino-builder ${LIBRARY_PATH_OPTS} \
		-fqbn ${BOARD} -hardware ${HARDWARE} -tools '' -build-cache ${BUILD_PATH} -build-path ${BUILD_PATH} \
		-compile ${ARDUINO_BUILDER_COMPILE_OPTS} ${MAIN_SOURCE}

upload : ${HEX_FILE}
	arduino-cli upload -b ${BOARD} -p ${PORT} -i ${HEX_FILE} ${ARDUINO_CLI_UPLOAD_OPTS}

console:
	${MAKEFILE_DIR}/console.sh -p ${PORT} -s ${BAUDRATE} -b ${BOARD} ${ARDDUDE_OPTIONS} -f ${HEX_FILE}
	
