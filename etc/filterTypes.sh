#!/bin/sh

# eclipse indexer don't know existence of types like uint8_t
# the "define" list generated by gcc -E -dM contains definitions like __UINT8_TYPE__
# as a workaround, this script use those macros to define uint8_t as another macro 

sed -n 's/#define __\([A-Z0-9]*\)_TYPE__ \(.*\)/#define \1_t \2/p' $* \
| tr 'A-Z' 'a-z'