#cRSID makefile

LIBFOLDER = libcRSID

#CRSID_PLATFORM = 1
include $(LIBFOLDER)/Config.mk
include $(LIBFOLDER)/Common.mk


APPDIR = /usr/bin
APPNAME = crsid
APPSOURCE = cRSID.c GUI/GUI.c
APPSOURCE_STATIC_CLI = cRSID.c $(LIBFOLDER)/libcRSID.c
APPSOURCE_STATIC_GUI = $(APPSOURCE_STATIC_CLI) GUI/GUI.c
APPSOURCE_STATIC = $(APPSOURCE_STATIC_GUI)

LIBHEADER = $(LIBFOLDER)/libcRSID.h
LIBSOURCE = $(LIBFOLDER)/libcRSID.c
LIBSOURCES = $(LIBHEADER) $(LIBSOURCE)

SUBSOURCES_CLI =
SUBSOURCES_GUI = GUI/*.c
SUBSOURCES = $(SUBSOURCES_GUI)

LIBSHARED = $(LIBFOLDER)/libcRSID.so
LIBSTATIC = $(LIBFOLDER)/libcRSID.a


SDL_DIR=SDL
# HERMES_DIR=$(SDL_DIR)/src/hermes
SDL_INTERNAL_SOURCES = -DUSING_INTERNAL_SDL=1  $(SDL_DIR)/src/*.c  $(SDL_DIR)/src/stdlib/*.c
SDL_INTERNAL_SOURCES += $(SDL_DIR)/src/video/*.c  $(SDL_DIR)/src/audio/*.c  $(SDL_DIR)/src/events/*.c  $(SDL_DIR)/src/timer/*.c  $(SDL_DIR)/src/thread/*.c
SDL_INTERNAL_SOURCES += $(SDL_DIR)/src/cpuinfo/*.c  $(SDL_DIR)/src/file/*.c  $(SDL_DIR)/src/loadso/dlopen/*.c
 SDL_INTERNAL_SOURCES_UNIX = $(SDL_DIR)/src/video/x11/*.c  $(SDL_DIR)/src/audio/alsa/*.c  $(SDL_DIR)/src/timer/unix/*.c  $(SDL_DIR)/src/thread/pthread/*.c  # $(SDL_DIR)/src/video/fbcon/*.c
SDL_INTERNAL_SOURCES += -I$(SDL_DIR)/include  -lm  -lpthread  -lX11  -lXext  -lasound  -D_GNU_SOURCE=1
CCFLAGS += -Wno-missing-field-initializers -Wno-sign-compare -Wno-maybe-uninitialized #suppress internal SDL warnings
 CCFLAGS +=  -Wno-deprecated-declarations  # to suppress warnings about some deprecated SDL libX11 functions in internal SDL1.2
CCFLAGS += -ldl


all: full


full: CCFLAGS+=-O3
full: app libs


#app: CCFLAGS+=-O3
app: CCFLAGS+=-s  # strip executable from debug info for the release
app: curvegen builtin  bin  alsa cli  intSDL


#Build with static/built-in compiling & linking of libcRSID source:
#shared: CC=tcc
ifneq ($(OS),Darwin)
bin: DEFINES+=-DLINUX
endif
bin: DEFINES += $(SDL_DEFINES)
bin: CCFLAGS += $(SDL_EXTERNAL_SOURCES)
bin: $(APPSOURCE_STATIC) $(LIBSOURCES) $(SUBSOURCES)  # builtin
	$(CC)  $(APPSOURCE_STATIC)  -o $(APPNAME)  $(DEFINES)  $(CCFLAGS)  # -s

#Build with static/built-in compiling & linking of libcRSID and libSDL sources (least amount of package-dependencies):
#shared: CC=tcc
ifneq ($(OS),Darwin)
intSDL: DEFINES+=-DLINUX
endif
intSDL: DEFINES += $(SDL_DEFINES)
intSDL: SDL_INTERNAL_SOURCES += $(SDL_INTERNAL_SOURCES_UNIX)
intSDL: $(APPSOURCE_STATIC) $(LIBSOURCES) $(SUBSOURCES)  # builtin
	$(CC)  $(APPSOURCE_STATIC) $(SDL_INTERNAL_SOURCES)   -o $(APPNAME)_intSDL  $(DEFINES)  $(CCFLAGS)  # -s


#a build that links the precompiled libcRSID shared library
#shared: CC=tcc
ifneq ($(OS),Darwin)
shared: DEFINES+=-DLINUX
endif
shared: DEFINES += $(SDL_DEFINES)
shared: CCFLAGS += $(SDL_EXTERNAL_SOURCES)
shared: $(APPSOURCE) $(SUBSOURCES) builtin  libs
	$(CC)  $(APPSOURCE)  -o $(APPNAME)  $(DEFINES)  $(CCFLAGS)  -L$(LIBFOLDER)  -lcRSID  -Wl,-rpath=$(LIBFOLDER)  # -s


#a build that links the precompiled libcRSID static library
#static: CC=tcc
ifneq ($(OS),Darwin)
static: DEFINES+=-DLINUX
endif
static: DEFINES += $(SDL_DEFINES)
static: CCFLAGS += $(SDL_EXTERNAL_SOURCES)
static: $(APPSOURCE) $(SUBSOURCES) builtin  libs
	$(CC)  $(APPSOURCE)  -o $(APPNAME)  $(DEFINES)  $(CCFLAGS)  $(LIBSTATIC)  # -s


#Build with static/built-in compiling & linking of libcRSID source with ALSA audio-backend and SDL video
#static: CC=tcc
alsa:
ifneq ($(OS),Darwin)
alsa: DEFINES+=-DLINUX
endif
alsa: DEFINES += $(LIBCRSID_PC_DEFINES) $(ALSA_DEFINES)
alsa: CCFLAGS += $(SDL_EXTERNAL_SOURCES) $(ALSA_SOURCES)
alsa: $(APPSOURCE_STATIC) $(LIBSOURCES) $(SUBSOURCES)  # builtin
	$(CC)  $(APPSOURCE_STATIC)  -o $(APPNAME)_alsa  $(DEFINES)  $(CCFLAGS)  # -s

#Build with static/built-in compiling & linking of libcRSID source with ALSA audio-backend and CLI (no SDL-dependence)
#static: CC=tcc
ifneq ($(OS),Darwin)
cli: DEFINES+=-DLINUX -DCLI_ONLY
endif
cli: DEFINES += $(ALSA_DEFINES)
cli: CCFLAGS += $(ALSA_SOURCES)
cli: $(APPSOURCE_STATIC_CLI) $(LIBSOURCES) $(SUBSOURCES_CLI)  # builtin
	$(CC)  $(APPSOURCE_STATIC_CLI)  -o $(APPNAME)_cli  $(DEFINES)  $(CCFLAGS)  # -s


libs: $(LIBSOURCES) $(SUBSOURCES)
	make -C $(LIBFOLDER)


curvegen:
	make -C $(LIBFOLDER) curvegen


#test: DEBUG=$(DEBUGGER)
test: CCFLAGS+=-g  # -O3
test: CC=tcc  #for debugging, using faster compiler for testing (comment out for gcc set in libcRSID/Common.mk)
test: bin
#	$(DEBUG) ./$(APPNAME) resources/music/Flash_It_Back.sid resources/music/Thiz_Iz_Da_Gizda_3SID.sid default-playlist.sil  # -info
	$(DEBUG) ./$(APPNAME) RoboCop_3.sid


testcli: CCFLAGS+=-g  # -O3
testcli: CC=tcc  #for debugging, using faster compiler for testing (comment out for gcc set in libcRSID/Common.mk)
testcli: bin
#	./$(APPNAME) -cli -info resources/music/Flash_It_Back.sid resources/music/Thiz_Iz_Da_Gizda_3SID.sid  default-playlist.sil  # -sidlight  # -info
	./$(APPNAME) -cli -info resources/music/RoboCop_3.sid  # -sidlight  # -info

debug: $(APPNAME)
	gdb --batch ./$(APPNAME) -ex "r" -ex "bt"  # -cli  # -info resources/music/Thiz_Iz_Da_Gizda_3SID.sid   # Flash_It_Back.sid

ifneq ($(OS),Darwin)
disass: DEFINES+=-DLINUX
endif
disass: CCFLAGS+=-S # -g -Wa,-adhln -fverbose-asm  # -O3
disass: $(APPSOURCE_STATIC) $(LIBSOURCES) $(SUBSOURCES)  # builtin
	$(CC)  $(APPSOURCE_STATIC)  $(DEFINES)  $(CCFLAGS)


font:
	$(CC) GUI/tools/fontgen.c -o GUI/tools/fontgen -lm
	GUI/tools/fontgen

builtin:
	$(CC)  $(LIBFOLDER)/tools/bin2array.c  -o $(LIBFOLDER)/tools/bin2array
	$(LIBFOLDER)/tools/bin2array resources/builtin-music.sid


package: DEBFOLDER=deb-package-content
package:
	mkdir -p $(DEBFOLDER)/usr/bin $(DEBFOLDER)/usr/lib $(DEBFOLDER)/usr/include $(DEBFOLDER)/usr/lib/pkgconfig
	mkdir -p $(DEBFOLDER)/usr/share/icons $(DEBFOLDER)/usr/share/pixmaps
	mkdir -p $(DEBFOLDER)/usr/share/applications $(DEBFOLDER)/usr/share/mime/packages $(DEBFOLDER)/DEBIAN
	cp -a  crsid  crsid_cli  crsid_alsa  crsid_intSDL  crsid.sh crsida  $(DEBFOLDER)/usr/bin/
	cp -a resources/cRSID-icon.png  $(DEBFOLDER)/usr/share/icons/
	cp -a resources/cRSID-icon.png  $(DEBFOLDER)/usr/share/pixmaps/
	cp -a resources/x-cRSID-extension-sid.xml resources/x-cRSID-extension-sil.xml  $(DEBFOLDER)/usr/share/mime/packages/
	cp -a resources/cRSID.desktop  $(DEBFOLDER)/usr/share/applications/
	cp -a  $(LIBSHARED)  $(LIBSTATIC)  $(DEBFOLDER)/usr/lib/
	cp -a  $(LIBHEADER)  $(DEBFOLDER)/usr/include/
	cp -a  $(LIBFOLDER)/libcRSID.pc  $(DEBFOLDER)/usr/lib/pkgconfig
	chmod 777 $(DEBFOLDER)/usr/bin/crsid $(DEBFOLDER)/usr/bin/crsid_cli $(DEBFOLDER)/usr/bin/crsid_alsa $(DEBFOLDER)/usr/bin/crsid_intSDL
	chmod 777 $(DEBFOLDER)/usr/bin/crsida $(DEBFOLDER)/usr/bin/crsid.sh
	echo "Package: crsid\nVersion: $(CRSID_VERSION) \nSection: custom\nPriority: optional\nArchitecture: \
          all\nEssential: no\nInstalled-Size: $$(du -s --apparent-size $(DEBFOLDER) | cut -f 1)\nDepends: $(DEPENDS)\nMaintainer: Hermit (Mihaly Horvath)\nDescription: cRSID C64 SID-music player" \
          > $(DEBFOLDER)/DEBIAN/control
	dpkg-deb --build $(DEBFOLDER) && mv $(DEBFOLDER).deb crsid-$(CRSID_VERSION)-$$(uname -m).deb
	rm -r -f $(DEBFOLDER)

package_nonSDL: DEBFOLDER=deb-package-content
package_nonSDL:
	mkdir -p $(DEBFOLDER)/usr/bin
	mkdir -p $(DEBFOLDER)/usr/share/icons $(DEBFOLDER)/usr/share/pixmaps
	mkdir -p $(DEBFOLDER)/usr/share/applications $(DEBFOLDER)/usr/share/mime/packages $(DEBFOLDER)/DEBIAN
	cp -a  crsid_cli crsid_intSDL  crsid.sh crsida  $(DEBFOLDER)/usr/bin/
	cp -a resources/cRSID-icon.png  $(DEBFOLDER)/usr/share/icons/
	cp -a resources/cRSID-icon.png  $(DEBFOLDER)/usr/share/pixmaps/
	cp -a resources/x-cRSID-extension-sid.xml resources/x-cRSID-extension-sil.xml  $(DEBFOLDER)/usr/share/mime/packages/
	cp -a resources/cRSID.desktop  $(DEBFOLDER)/usr/share/applications/
	chmod 777 $(DEBFOLDER)/usr/bin/crsid_cli $(DEBFOLDER)/usr/bin/crsid_intSDL
	chmod 777 $(DEBFOLDER)/usr/bin/crsida $(DEBFOLDER)/usr/bin/crsid.sh
	echo "Package: crsid-nonsdl\nVersion: $(CRSID_VERSION) \nSection: custom\nPriority: optional\nArchitecture: \
          all\nEssential: no\nInstalled-Size: $$(du -s --apparent-size $(DEBFOLDER) | cut -f 1)\nDepends:libc6 , libasound2\nMaintainer: Hermit (Mihaly Horvath)\nDescription: cRSID C64 SID-music player (without libSDL dependency)" \
          > $(DEBFOLDER)/DEBIAN/control
	dpkg-deb --build $(DEBFOLDER) && mv $(DEBFOLDER).deb crsid-$(CRSID_VERSION)-nonSDL-$$(uname -m).deb
	rm -r -f $(DEBFOLDER)

install: # full $(LIBSHARED) $(LIBSTATIC)  # package
	dpkg -r crsid && dpkg -i crsid-$(CRSID_VERSION)-$$(uname -m).deb
#	$(CP)  $(LIBSHARED) $(LIBSTATIC)  $(LIBDIR)
#	$(CP)  $(LIBHEADER)  $(INCDIR)
#	$(CP)  $(APPNAME) crsida crsid.sh  $(APPDIR)


uninstall:
	dpkg -r crsid
	$(RM) $(APPDIR)/$(APPNAME) $(APPDIR)/crsida $(APPDIR)/crsid.sh $(LIBDIR)/$(LIBSHARED) $(LIBDIR)/$(LIBSTATIC) $(INCDIR)/$(LIBHEADER)


clean:
	$(RM) $(APPNAME) $(APPNAME)_alsa $(APPNAME)_cli $(APPNAME)_intSDL  # $(APPNAME).exe $(APPNAME).com  # $(APPNAME)_mac
	$(RM) package  # resources/tools/bin2array  # GUI/graphics/fonts*.h
#	$(RM) *.s
#	$(RM) *.deb
	$(RM) $(LIBFOLDER)/*.o $(LIBFOLDER)/*.a $(LIBFOLDER)/*.so  # $(LIBFOLDER)/tools/curvegen


purge: clean uninstall


help:
	@echo ""
	@echo "cRSID Makefile help, targets ('all' is the default which creates 'crsid' target):"
	@echo "all, app, bin, alsa, test, testcli, debug, libs, package, package_nonSDL, install, uninstall, clean, purge"
	@echo ""


#targets for a system whose name I won't pronounce anymore if at all possible:


ico:
	icotool  -c  -o resources/cRSID-icon.ico  resources/cRSID-icon.png   #icotool is in 'icoutils' package on Debian Linux
	i686-w64-mingw32-windres -O coff -i resources/cRSID-icon.rc -o resources/cRSID-icon.res

# need to build SDL1.2 with i686-w64-mingw32-gcc: ./configure --target=i686-w64-mingw32 --host=i686-w64-mingw32 --build=x86_64-linux --prefix=/usr/local/i686-w64-mingw32 --enable-shared
$(APPNAME).exe: $(APPSOURCE) $(LIBSOURCES) $(SUBSOURCES) builtin curvegen
	i686-w64-mingw32-gcc  $(APPSOURCE_STATIC)  resources/cRSID-icon.res  -o $(APPNAME).exe  $(DEFINES)  -DWINDOWS  \
    -O3  -lmingw32  -mwindows  -lSDLmain  -lSDL  \
    -I/usr/local/i686-w64-mingw32/include/SDL  -L/usr/local/i686-w64-mingw32/lib  -g  -s  # -Wl,-subsystem,console
	sed -i 's/stdout.txt/\x0\x0\x0\x0\x0\x0\x0\x0\x0\x0/' $(APPNAME).exe
	sed -i 's/stderr.txt/\x0\x0\x0\x0\x0\x0\x0\x0\x0\x0/' $(APPNAME).exe

$(APPNAME).com: $(APPSOURCE) $(LIBSOURCES) $(SUBSOURCES) builtin curvegen
	i686-w64-mingw32-gcc  $(APPSOURCE_STATIC)  resources/cRSID-icon.res  -o $(APPNAME).com  $(DEFINES)  -DWINDOWS  \
    -O3  -Wl,-subsystem,console  -lmingw32  -lSDLmain  -lSDL  \
    -I/usr/local/i686-w64-mingw32/include/SDL  -L/usr/local/i686-w64-mingw32/lib  -g  -s
	sed -i 's/stdout.txt/\x0\x0\x0\x0\x0\x0\x0\x0\x0\x0/' $(APPNAME).com
	sed -i 's/stderr.txt/\x0\x0\x0\x0\x0\x0\x0\x0\x0\x0/' $(APPNAME).com

testw:
	wine $(APPNAME).exe resources/music/E_G_Blues_2SID.sid

debugw:
	winedbg --gdb $(APPNAME).exe



