#ifndef CRSID_HELPERS_HEADER__GUI
#define CRSID_HELPERS_HEADER__GUI //used  to prevent double inclusion of this header-file


#define CRSID_USER_SUBFOLDER ".cRSID"
#define PLAYLIST_FILE_EXTENSION ".sil"

#define DEFAULT_PLAYLIST_FILENAME "DefaultPlaylist"PLAYLIST_FILE_EXTENSION
#define CONFIGFILE_NAME "DefaultSettings.cfg"

#define SONGLENGTHS_DATAFILE_NAME "Songlengths.md5" //as downloaded from HVSC
#define KERNAL_ROM_DATAFILE_NAME "kernal" //as taken from C64 or e.g. VICE emulator source archive
#define BASIC_ROM_DATAFILE_NAME "basic" //as taken from C64 or e.g. VICE emulator source archive

#define BOTTOM_PLAYLIST 0  //having playlist next to main GUI (instead of below) is more screenlayout-friendly


enum { DIRENTRIES_MAX = 1000 };


//extern char FileDialogDirectory [CRSID_PATH_LENGTH_MAX+2];

//void startNextPlayListItem ();
//void startPrevPlayListItem ();


#endif //CRSID_HELPERS_HEADER__GUI