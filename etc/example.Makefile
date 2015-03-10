## specify root dir of AMM project
AMM_DIR := /SRC/GitPiif/arddude

## that's the minimum to do.

## but we may specify a default target
export TARGET_BOARD ?= uno

## and a default port
export UPLOAD_PORT ?= com4

## if its a library project, specify it
#TODO=lib

## else, we may specify main source file, to give its name to output file
## else current directory name will be used as name, and a .cpp or .ino file
##with the same name, or main.cpp or main.ino must exist
# export MAIN_SOURCE=my_main.cpp

## then include AMM main Makefile
include $(AMM_DIR)/etc/main.mk

## after this inclusion, we may add some stuff like our include path
INCLUDE_FLAGS += -Ilibraries
