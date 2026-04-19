// cRSID - a lightweight (integer-only) RealSID playback environment by Hermit

#if (CLI_ONLY)
 #define ENABLED_GUI 0
#else
 #define ENABLED_GUI 1
#endif


#ifndef CRSID_PLATFORM_PC
 #define CRSID_PLATFORM_PC
#endif
#ifdef DARWIN
 #include <SDL.h>
 #include <SDL_main.h>
#endif

 #include <termio.h>
#ifdef LINUX
 #include <termios.h> //<curses.h>
 #include <sys/stat.h> //for mkdir(name,mode)
 #define DIR_SEPARATOR "/"
#elif defined(WINDOWS)
 #include <conio.h>
 #include <io.h> //for mkdir()
 //#include <direct.h> //for mkdir()
 #define DIR_SEPARATOR "\\"
#endif

//#define LIBCRSID_SOURCE //allows reaching extra internal/private functions
#include "libcRSID/libcRSID.h"
//#undef LIBCRSID_SOURCE

#if (ENABLED_GUI)
#include "GUI/GUI.h" //#include "GUI/GUI.c"
#endif

#include "cRSID_helpers.c"

#include "resources/builtin-music.h"


#define DEFAULT_SUBTUNE 1 //1..
#define BUILTIN_MUSIC_DEFAULT_SUBTUNE 1
#define DEFAULT_SAMPLERATE 44100
#define CLI_KEYCHECK_PERIOD 10000 //5000 //microseconds
#define BUFFERLENGTH_MIN       256
#define BUFFERLENGTH_DEFAULT  8192
#define BUFFERLENGTH_MAX     32768


char* cRSIDfolderName = "";

const char FFWD_SPEED = 4; //times

const char ChannelChar[] = {'-','L','R','M'};

unsigned char* HelpText = (unsigned char*)
                                 "   cRSID SID music player by Hermit\n"
                                 "\n"
                                 "-To play .sid/.sid.seq files, give filename\n"
                                 " as argument or associate them to crsid.\n"
                                 "-You can give a textual .sil file containing\n"
                                 " a filelist to have a customized playlist.\n"
                                 "-If you give a foldername instead (even '.'),\n"
                                 " all the SID music-files in the subfolders\n"
                                 " will be added to the playlist automatically.\n"
                                 "-If no argument is given to crted it seeks\n"
                                 " music/playlist-files in the current folder.\n"
                                 "(You can combine all these, see README.)\n";



//======================================================================================



int main (int argc, char *argv[]) {
 #ifdef LINUX
  enum CursorKeys { KEY_LEFT=0x44, KEY_RIGHT=0x43, KEY_UP=0x41, KEY_DOWN=0x42 };
 #elif defined(WINDOWS)
  enum CursorKeys { KEY_LEFT=0x4B, KEY_RIGHT=0x4D, KEY_UP=0x48, KEY_DOWN=0x50 };
 #elif defined(DARWIN)
  enum CursorKeys { KEY_LEFT=276, KEY_RIGHT=275, KEY_UP=273, KEY_DOWN=274 };
 #endif

 int i, Tmp, ArgFileIdx=0, ArgSubtuneIdx=0, ArgMainVolume=0, ArgPlayTime=0;
 FILE *fp;
 char Info=0/*, CLImode=0*/;
 int PressedKeyChar=0, KeyHoldCnt=0, Exit=0;
 char SubTune=0, CIAisSet = 0; char* TmpString = NULL;
 cRSID_SIDheader *SIDheader;
 cRSID_Interface *cRSID_API; //cRSID_C64instance *C64;  //now only for testing if initialization was successful
 char *SIDfileName = "";
 int PrevFrameCycles;


 i=1; cRSID.SampleBufferLength=BUFFERLENGTH_DEFAULT;  //handling arguments needed to be set before cRSID_init
 while (i<argc) {
  if (!strcmp(argv[i],"-buflen") || !strcmp(argv[i],"-bufsize")) { //'bufsize' was a bit misleading because 'size' usually means the amount of bytes, but 'length' correctly the amount of objects (samples/frames)
   ++i;
   if(i<argc) {
    sscanf(argv[i],"%d",&cRSID.SampleBufferLength);
    if(cRSID.SampleBufferLength<256) cRSID.SampleBufferLength=256;
    if(32768<cRSID.SampleBufferLength) cRSID.SampleBufferLength=32768;
   }
  }
  ++i;
 }
 cRSID_API = cRSID_init( DEFAULT_SAMPLERATE, cRSID.SampleBufferLength );
 if (cRSID_API==NULL) exit(CRSID_ERROR_INIT);

 cRSIDfolderName = getCRSIDfolderPath();
 if ( !folderExists( cRSIDfolderName ) ) {
  if ( mkdir( cRSIDfolderName
  #ifndef WINDOWS
   , 0777
  #endif
  ) == -1 ) printf( "Couldn't create an optional cRSID folder \"%s\" for config/etc.\n", cRSIDfolderName );
  else printf( "cRSID folder \"%s\" for config/etc got created.\n", cRSIDfolderName );
 }
 loadConfigFile();

 cRSID_setBuiltInMusic( builtin_music, sizeof(builtin_music) );
 cRSID_setCallBack__autoAdvance ( printTuneInfo_callBack, (void*) &PrevFrameCycles );

 #ifdef WINDOWS //have output on console instead of txt files
  freopen("CON", "w", stdout); freopen("CON", "w", stderr);
 #endif

 i=1; ArgFileIdx=ArgSubtuneIdx=0;
 cRSID.CLImode =
  #if (CLI_ONLY)
   1;
  #else
   0;
  #endif

 while (i<argc) {
  if (!strcmp(argv[i],"-h") || !strcmp(argv[i],"-?") || !strcmp(argv[i],"-help") || !strcmp(argv[i],"--h") || !strcmp(argv[i],"--help")) printUsage();
  else if (!strcmp(argv[i],"-info")) Info=1;
  else if (!strcmp(argv[i],"-cli")) cRSID.CLImode=1;
  else if (!strcmp(argv[i],"-sid6581")) { cRSID.SelectedSIDmodel=6581; }
  else if (!strcmp(argv[i],"-sid8580")) { cRSID.SelectedSIDmodel=8580; }
  else if (!strcmp(argv[i],"-mono"))   { cRSID.Stereo = CRSID_CHANNELMODE_MONO; } //mono/stereo
  else if (!strcmp(argv[i],"-stereo") || !strcmp(argv[i],"-wide")) { cRSID.Stereo = CRSID_CHANNELMODE_STEREO; } //mono/stereo
  else if (!strcmp(argv[i],"-narrow")) { cRSID.Stereo = CRSID_CHANNELMODE_NARROW; } //narrow stereo
  else if (!strcmp(argv[i],"-sidhq")) { cRSID.HighQualitySID=1; } //high-quality SID with 5x wavesample-rate (good for combined waveforms)
  else if (!strcmp(argv[i],"-sidlight")) { cRSID.HighQualitySID=0; } //cRSID-light emulation (sharper Hermit-style waveforms, more noisy combined waveforms)
  else if (!strcmp(argv[i],"-resamplehq") || !strcmp(argv[i],"-sinc")) { cRSID.HighQualitySID=1; cRSID.HighQualityResampler=1; } //use high-quality but fast integer-based Sinc-interpolation FIR resampler (low-pass filtered triangles on all channels are noisier)
  else if (!strcmp(argv[i],"-resamplight") || !strcmp(argv[i],"-resamplelight")) { cRSID.HighQualitySID=1; cRSID.HighQualityResampler=0; } //use fast averaging resampler instead of high-quality Sinc-based FIR resampler
  else if (!strcmp(argv[i],"-autoexit")) { cRSID.AutoExit=1; cRSID.AutoAdvance=1; } //auto-advance in subtunes or exit
  else if (!strcmp(argv[i],"-noadvance")) cRSID.AutoAdvance=0;
  else if (!strcmp(argv[i],"-nofadeout")) { cRSID.FadeOut=0; }
  else if (!strcmp(argv[i],"-volume")) {
   ++i;
   if(i<argc) {
    sscanf(argv[i],"%d",&ArgMainVolume);
    if(ArgMainVolume<0) cRSID.MainVolume=0;
    else if(255<ArgMainVolume) cRSID.MainVolume=255;
    else cRSID.MainVolume = ArgMainVolume;
   }
  }
  else if (!strcmp(argv[i],"-playtime")) {
   ++i;
   if(i<argc) {
    sscanf(argv[i],"%d",&ArgPlayTime);
    if(ArgPlayTime<0) cRSID.FallbackPlayTime=0;
    else if(59*60<ArgPlayTime) cRSID.FallbackPlayTime = 59*60;
    else cRSID.FallbackPlayTime = ArgPlayTime;
   }
  }
  else if (!strcmp(argv[i],"-songlengths")) { ++i; if (i<argc) loadSonglengthsFile( argv[i], 1 ); }
  else if (!strcmp(argv[i],"-kernal")) { ++i; if (i<argc) loadKERNALfile( argv[i], 1 ); }
  else if (!strcmp(argv[i],"-basic")) { ++i; if (i<argc) loadBASICfile( argv[i], 1 ); }
  else if ( cRSID_playableExtension(argv[i]) ) { //!strcmp(getFileExtension(argv[i]),".sid") || !strcmp(getFileExtension(argv[i]),".SID") ) { //check file-extension and/or magic-string!
   if ((fp=fopen(argv[i],"rb")) != NULL ) { //add file given as argument to playlist
      fclose(fp); if (cRSID.PlayListSize>0) addToPlayList("File given as argument:",0, 0,0);
      if (ArgFileIdx==0) { ArgFileIdx = i; cRSID.PlayListPlayPosition = cRSID.PlayListSize; } //if more given, still start at the first one
      addToPlayList(argv[i],1, 0,0);
    }
  }
  else if ( cRSID_compareFileExtension(argv[i],PLAYLIST_FILE_EXTENSION) ) { //process playlist-file into internal playlist
   chdir(cRSID_folderNameOnly(argv[i])); processListFile( argv[i] ); //gives back starting point for relative-paths
  }
  else if (sscanf(argv[i],"%d",&Tmp)==1 && 1<Tmp && Tmp<256) { SubTune=Tmp; ArgSubtuneIdx=i; } //else { sscanf(argv[i],"%d",&SubTune); if(1<SubTune && SubTune<256) ArgSubtuneIdx=i; else SubTune=DEFAULT_SUBTUNE; }
  else { //search playable files in given folder recursively and add them to playlist
   findSortSIDfiles(argv[i],1);
  }
  ++i;
 }

 //If not given as argument, albeit optional, try to look for songlengths/ROMfiles at default locations (home folder and .cRSID folder)
 if (cRSID.SongLengths == NULL) loadSonglengthsFile( addHomeFolderPath( SONGLENGTHS_DATAFILE_NAME ), 0 );
 if (cRSID.SongLengths == NULL) loadSonglengthsFile( makePath( cRSIDfolderName, SONGLENGTHS_DATAFILE_NAME ), 1 );
 if (cRSID.KERNALfileData == NULL) loadKERNALfile( addHomeFolderPath( KERNAL_ROM_DATAFILE_NAME ), 0 );
 if (cRSID.KERNALfileData == NULL) loadKERNALfile( makePath( cRSIDfolderName, KERNAL_ROM_DATAFILE_NAME ), 1 );
 if (cRSID.BASICfileData == NULL) loadBASICfile( addHomeFolderPath( BASIC_ROM_DATAFILE_NAME ), 0 );
 if (cRSID.BASICfileData == NULL) loadBASICfile( makePath( cRSIDfolderName, BASIC_ROM_DATAFILE_NAME ), 1 );

 if (ArgFileIdx > 0) { SIDfileName = argv[ArgFileIdx]; } //else { printUsage(); return 0; }
 else if (cRSID.PlayListSize==0) { //if no file/folder/playlist argument given, scan current folder as a fallback
  if (cRSID.CLImode || Info) printUsage();
  findSortSIDfiles(".",0);
  if (cRSID.PlayListSize>0 ) setFirstPlayableItem( &SIDfileName );
  else if (findListFile(".")) { setFirstPlayableItem( &SIDfileName ); }
  if (cRSID.PlayListSize==0) { //if nothing found at all, fallback to default playlist (if found) or built-in music and help
   if ( fileExists( TmpString = makePath( cRSIDfolderName, DEFAULT_PLAYLIST_FILENAME ) ) ) {
    processListFile( TmpString );  if(SubTune==0) SubTune=1; setFirstPlayableItem( &SIDfileName );
    if ( !fileExists( SIDfileName ) ) {
     cRSID.BuiltInMusic=1; cRSID.PlayListSize=1;
     if(SubTune==0) SubTune=BUILTIN_MUSIC_DEFAULT_SUBTUNE;
    }
   }
   else {
    cRSID.BuiltInMusic=1; cRSID.PlayListSize=1;
    if(SubTune==0) SubTune=BUILTIN_MUSIC_DEFAULT_SUBTUNE;
   }
   cRSID.AutoAdvance=1;
  }
 }
 else setFirstPlayableItem( &SIDfileName );

 //--------------------------------------------------------------------------------------------

 if ( (SubTune==0 && Info==0 && cRSID.PlayListSize<=1) || cRSID.CLImode==0 ) {
  if (cRSID.CLImode || SubTune==0) SubTune=DEFAULT_SUBTUNE/*1*/; //else sscanf(argv[2],"%d",&SubTune);
  cRSID.SIDheader = SIDheader = cRSID_playSIDfile( SIDfileName, SubTune );
  if (SIDheader == NULL) { printf("Load error! (Single/first file not found.)\n"); return CRSID_ERROR_LOAD; }
 }
 else { //CLI detailed playback

  //sscanf(argv[2],"%d",&SubTune);
  if ( (SIDheader = cRSID_loadSIDtune(SIDfileName)) == NULL ) { printf("Load error!\n"); return CRSID_ERROR_LOAD; }

  //startsubtune:
  cRSID_initSIDtune( SIDheader,SubTune ); //cRSID.PlaybackSpeed=1; //cRSID.Paused=0;

  printTuneInfo_callBack( 0, (void*) &PrevFrameCycles ); //printTuneInfo( 0, &PrevFrameCycles );

  cRSID_playSIDtune();

  usleep( 100000 );
  if ( cRSID.FrameCycles != PrevFrameCycles ) {
   if(!CIAisSet) { CIAisSet=1; printf("New FrameSpeed: %.1fx (%d cycles between playercalls)\n",
                                      (cRSID.VideoStandard<=1? 19656.0:17095.0) / cRSID.FrameCycles, cRSID.FrameCycles); }
  }

 }

 cRSID.PlaybackSpeed=1; cRSID.Paused=0;


 enum { KEY_PREFIX = 27, KEY_PREFIX2 = 91, KEY_ESC = -1, KEY_NONE = 0, FFWD_KEYHOLD_TIME = 50 /*ms*/ * 1000 / CLI_KEYCHECK_PERIOD };

 if (cRSID.CLImode) {
  printf("\nPress ESC to abort playback, ENTER to restart, SPACE to pause/continue, hold TAB for fast-forward,\n"
           "1..9/Left/Right for subtune/next/previous, Up/Down:Volume\n\n"); //getchar();
  setKeyCapture(1);
  while(!Exit && cRSID.PlaytimeExpired==0) {
   #ifdef LINUX
    ioctl( STDIN_FILENO, FIONREAD, &PressedKeyChar ); //get number of buffered incoming pressedkey-characters
    if (PressedKeyChar > 0) { /*while(PressedKeyChar > 1)*/
     PressedKeyChar = getchar();  //printf("%d\n",PressedKeyChar);
     if (PressedKeyChar==KEY_PREFIX) { //double-character keys starting with KEY_PREFIX, ending with the proper keycode
      PressedKeyChar = getchar();  //printf(" %d\n",PressedKeyChar);
      if (PressedKeyChar==KEY_PREFIX2) {
       PressedKeyChar = getchar();  //printf("  %d\n",PressedKeyChar);
    }}}
    else PressedKeyChar = KEY_NONE;
   #elif defined(WINDOWS)
    PressedKeyChar = getch();
   #elif defined(DARWIN)
    PressedKeyChar = getchar();
   #endif
   //printf("%2.2X\n",PressedKeyChar);
   if (PressedKeyChar==KEY_ESC /*27*/ /*|| PressedKeyChar=='\n' || PressedKeyChar=='\r'*/) { Exit=1; } //ESC/Enter?
   else if (PressedKeyChar==0x09 || PressedKeyChar=='`' || KeyHoldCnt > 0) {
    if (KeyHoldCnt > 0) --KeyHoldCnt; else KeyHoldCnt = FFWD_KEYHOLD_TIME;
     /*if(cRSID.PlaybackSpeed==1)*/ cRSID.PlaybackSpeed = FFWD_SPEED; /*else cRSID.PlaybackSpeed = 1;*/
   }
   else if (PressedKeyChar==' ') { cRSID.PlaybackSpeed=1; cRSID.Paused^=1; if(cRSID.Paused) cRSID_pauseSIDtune(); else cRSID_playSIDtune(); }
   else if (PressedKeyChar=='\n' || PressedKeyChar=='\r') { cRSID_startSubtune(cRSID.SubTune); } //printTuneInfo_callBack( 1, (void*) &PrevFrameCycles ); }
   else if ('1'<=PressedKeyChar && PressedKeyChar<='9') { SubTune=PressedKeyChar-'1'+1; cRSID_startSubtune(SubTune); printTuneInfo_callBack( 1, (void*) &PrevFrameCycles ); } //cRSID_pauseSIDtune(); goto startsubtune; }
   else if (PressedKeyChar==KEY_RIGHT) { printTuneInfo_callBack( nextTuneButton(), (void*) &PrevFrameCycles ); }
   else if (PressedKeyChar==KEY_LEFT) { printTuneInfo_callBack( prevTuneButton(), (void*) &PrevFrameCycles ); }
   else if (PressedKeyChar==KEY_UP) { volumeUpButton(); }
   else if (PressedKeyChar==KEY_DOWN) { volumeDownButton(); }
   else /*if (PressedKeyChar==KEY_NONE)*/ cRSID.PlaybackSpeed=1;
   #if (USING_AUDIO_LIBRARY__ALSA)
    cRSID_syncGenSamples( CLI_KEYCHECK_PERIOD );
   #else  //#elif (USING_AUDIO_LIBRARY__SDL)
    usleep( CLI_KEYCHECK_PERIOD );
   #endif
  }
  setKeyCapture(0);
 }

 else { //GUI
 #if (ENABLED_GUI)
   initGUI();
  #ifdef WINDOWS //have output on console instead of txt files
   freopen("CON", "w", stdout); freopen("CON", "w", stderr);
  #endif
  mainLoop();
 #endif
 }

 saveConfigFile();
 cRSID_close();
 if (cRSID.SongLengths != NULL) free( cRSID.SongLengths );
 if (cRSID.BASICfileData != NULL) free( cRSID.BASICfileData );
 if (cRSID.KERNALfileData != NULL) free( cRSID.KERNALfileData );
 return 0;
}


