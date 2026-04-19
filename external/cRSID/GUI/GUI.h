#ifndef LIBCRSID_HEADER__GUI
#define LIBCRSID_HEADER__GUI //used  to prevent double inclusion of this header-file


#include "../libcRSID/libcRSID.h"  //#include "../C64/C64.h"
#include "../libcRSID/Optimize.h"

#include "../cRSID_helpers.h"


typedef unsigned char byte; //typedef uint8_t byte;
typedef unsigned short word; //typedef uint16_t word;
typedef unsigned int  dword; //typedef uint32_t word;


//static unsigned char* HelpText;

void initGUI ();

void mainLoop ();

char prevTuneButton ();
char nextTuneButton ();
void volumeUpButton ();
void volumeDownButton ();


extern byte FirstDraw;

extern const char FFWD_SPEED;
extern unsigned char* HelpText;
extern const char ChannelChar [];
extern char* cRSIDfolderName;

extern char FileDialogDirectory [CRSID_PATH_LENGTH_MAX+2];


extern void addToPlayList (char* filename, char checkextension, char minutes, char seconds);
extern char* makePath (char* folderpath, char* filename);

extern void startNextPlayListItem ();
extern void startPrevPlayListItem ();


#endif //LIBCRSID_HEADER__GUI