# TODO ...

MAKEFILE_DIR := $(patsubst %/,%, $(dir $(realpath ${MAKEFILE_LIST})))

# deduce some info from source directory
PROJECT_DIR := ${CURDIR}
$(info running from ${PROJECT_DIR})

PROJECT_NAME:= $(notdir ${PROJECT_DIR})
MAIN_SOURCE := ${PROJECT_NAME}.ino

BUILD_PATH := ${PROJECT_DIR}/build

# allow user to add :
# - specific options to arduino-cli compile and upload commands
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

$(info Using board=${BOARD} port=${PORT} baudrate=${BAUDRATE})

ELF_FILE := ${BUILD_PATH}/${PROJECT_NAME}.${BOARD_DOT}.elf
HEX_FILE := ${BUILD_PATH}/${PROJECT_NAME}.${BOARD_DOT}.hex
DEP_FILE := ${BUILD_PATH}/${PROJECT_NAME}.deps

# rules

.PHONY: clean compile upload console all

all: compile

clean:
	rm -rf ${BUILD_PATH}

# TODO : how to depend to library changes or other .h/.c files ?
${DEP_FILE}: ${MAIN_SOURCE}
	@# preprocess phase creates a includes.json file
	arduino-cli compile -b ${BOARD} --build-path ${BUILD_PATH}/tmp --preprocess > /dev/null
	@# deduce source dependencies
	jq --arg SRC ${MAIN_SOURCE} \
		-r 'map(select(.Include != "") | $$SRC+": "+.Includepath+"/"+.Include)[]' \
		< ${BUILD_PATH}/tmp/includes.cache > ${DEP_FILE}

-include ${DEP_FILE}

compile : ${HEX_FILE}

${HEX_FILE} : ${DEP_FILE} ${MAIN_SOURCE}
	arduino-cli compile -b ${BOARD} ${ARDUINO_CLI_COMPILE_OPTIONS} --build-path ${BUILD_PATH}/tmp -o ${HEX_FILE} ${PROJECT_DIR}

upload : ${HEX_FILE}
	arduino-cli upload -b ${BOARD} -p ${PORT} -i ${HEX_FILE} ${ARDUINO_CLI_UPLOAD_OPTIONS}

console:
	${MAKEFILE_DIR}/console.sh -p ${PORT} -s ${BAUDRATE} -b ${BOARD} ${ARDDUDE_OPTIONS} -f ${HEX_FILE}
	
