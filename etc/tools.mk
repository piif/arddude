# some "functions" and tools to deal with pathes whatever the environment
#$(info tools.mk)

ifneq (${CYGWIN_HOME},)
  ARCH:=cygwin
  ## manage to avoid warning from make,avr-gcc and other cygwin binaries
  export CYGWIN=nodosfilewarning
else
  ifeq ($(shell uname),)
    ARCH:=windows
  else
    ARCH:=unix
  endif
endif

ifeq (ARCH,windows)
  BATCH_EXT := bat
else
  BATCH_EXT := sh
endif

## a working version of realpath / abspath
ifeq ($(realpath .),$(realpath $(realpath .)))
	truepath = $(realpath $1)
else
	## crazy case where c:/... is not seen as absolute => have to fake it
	## thanks to http://gnu-make.2324884.n4.nabble.com/using-realpath-abspath-in-3-81-td13798.html
	truepath = $(join \
             $(filter %:,$(subst :,: ,$1)),\
             $(realpath $(filter-out %:,$(subst :,: ,$1))))
endif

# Make does not offer a recursive wildcard function, so here's one:
# thank to http://stackoverflow.com/questions/3774568/makefile-issue-smart-way-to-scan-directory-tree-for-c-files
# takes 2 arguments : directory pattern
rwildcard=$(wildcard $(addsuffix $2, $1)) $(foreach d,$(wildcard $(addsuffix *, $1)),$(call rwildcard,$d/,$2))

# short version : equivalent of shell "find ."
findall=$1 $(foreach d,$(wildcard $(addsuffix /*/, $1)),$(call subdirs,$d))
# dump only directories (in fact, directories 
#subdirs=$(sort $(dir $(call findall, $1)))

subdirs=$(filter-out ./,$(sort $(dir $(wildcard $(1:%/=%)/*/))))
rsubdirs=$1 $(foreach d,$(filter-out $(1:%/=%)/,$(call subdirs,$1)),$(call rsubdirs,$d,$1))

## wildcards : take a directory and a list of patterns to set as suffix to this dir before calling wildcard
## + manage slashes between dir and pattern (since wildcard "x//*c" matches nothing and x*c may match wrong results)
wildcards=$(foreach p,$2,$(wildcard ${1:%/=%}/$p))

## filter-out by substring
filter-out-substr=$(foreach e,$2,$(if $(strip $(foreach p,$1,$(findstring $p,$e))),,$e))

tools-test:
	$(info TODO test some cases to verify that it works everywhere ...)
	$(info truepath for . should be $(realpath .) : '$(call truepath,.)')
	$(info truepath for $(realpath .) should be $(realpath .) : '$(call truepath,$(realpath .))')
	
	$(info subdirs .   => '$(call subdirs,.)')
	$(info subdirs of $(realpath .) : $(call subdirs,$(realpath .)))
	$(info subdirs etc => '$(call subdirs,etc)')
	$(info subdirs etc/=> '$(call subdirs,etc/)')
	$(info subdirs src => '$(call subdirs,src)')
	$(info subdirs src/=> '$(call subdirs,src/)')
	$(info subdirs src/main/resources => '$(call subdirs,src/main/resources)')
	$(info subdirs target/classes/cc => '$(call subdirs,target/classes/cc)')
	
	$(info rsubdirs .   => '$(call rsubdirs,.)')
	$(info rsubdirs etc => '$(call rsubdirs,etc)')
	$(info rsubdirs etc/=> '$(call rsubdirs,etc/)')
	$(info rsubdirs src => '$(call rsubdirs,src)')
	$(info rsubdirs src/=> '$(call rsubdirs,src/)')
	$(info rsubdirs src/main/resources => '$(call rsubdirs,src/main/resources)')
	$(info rsubdirs target/classes/cc => '$(call rsubdirs,target/classes/cc)')
	
	$(eval classes = $(call rsubdirs,target/classes))
	$(info classes = ${classes})
	$(info classes w/ cc = $(filter-out target/classes/cc/%,${classes}))

	$(info x out of aze qsd wxc rtyx cvb xyx mylk => $(call filter-out-substr,x,aze qsd wxc rtyx cvb xyx mylk))
	$(info x y out of aze qsd wxc rtyx cvb xyx mylk => $(call filter-out-substr,x y,aze qsd wxc rtyx cvb xyx mylk))

	$(info wildcards etc, *.sh *.bat => $(call wildcards,etc,*.sh *.bat))
	$(info wildcards etc/, *.sh *.bat => $(call wildcards,etc/,*.sh *.bat))

MAKEFILE_TOOLS_INCLUDED := true
