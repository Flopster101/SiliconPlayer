
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <ctype.h>
#include <unistd.h>

#include <dirent.h>
#include <sys/stat.h>

#ifdef LINUX
 #include <termios.h> //<curses.h>
#endif
#include <fcntl.h>

#include "GUI/GUI.h"

#include "cRSID_helpers.h"



char* getFileExtension(char *filename) { //get pointer of file-extension from filename string  //if no '.' found, point to end of the string
 char* LastDotPos = strrchr(filename,'.'); if (LastDotPos == NULL) return (filename+strlen(filename)); //make strcmp not to find match, otherwise it would be segmentation fault
 return LastDotPos;
}

char fileExists (char* path) { FILE *fp;  if ( (fp=fopen(path,"r")) != NULL ) { fclose(fp); return 1;} else return 0; }
char folderExists (char* path) { static struct stat st; return ( stat(path, &st) != -1 ); }
char isFile (char* path) { static struct stat st; stat(path, &st); return S_ISREG(st.st_mode); }
char isDir (char* path) { static struct stat st; stat(path, &st); return S_ISDIR(st.st_mode); }

void addToPlayList (char* filename, char checkextension, char minutes, char seconds) {
 if ( cRSID.PlayListSize < CRSID_PLAYLIST_ENTRIES_MAX && (!checkextension || cRSID_playableExtension(filename)) ) {
  strcpy( cRSID.PlayList[cRSID.PlayListSize], filename );
  if (minutes > 0) cRSID.PlayTimeMinutes[ cRSID.PlayListSize ] = minutes;
  if (seconds > 0) cRSID.PlayTimeSeconds[ cRSID.PlayListSize ] = seconds;
  if (checkextension) cRSID.PlayListNumbering[cRSID.PlayListSize] = cRSID.PlayListNumber++;
  ++cRSID.PlayListSize;
}}


static int cmpstr(char *string1, char *string2) { //compare strings alphabetically for sorting
 for (;;) {
  unsigned char char1 = tolower(*string1++); unsigned char char2 = tolower(*string2++);
  if (char1 < char2) return -1;
  if (char1 > char2) return 1;
  if ((!char1) || (!char2)) return 0;
 }
}

static inline void addToFileList (char* filename, char checkextension, char* filelist, int* filelist_size, int* filelist_hash) {
 if ( *filelist_size < DIRENTRIES_MAX && (!checkextension || cRSID_playableExtension(filename)) ) {
  strcpy( filelist+*filelist_size*CRSID_PATH_LENGTH_MAX, filename ); filelist_hash[*filelist_size]=*filelist_size; ++*filelist_size;
 }
}

static inline void sortFileList (char* filelist, int filelist_size, int* filelist_hash) {
 static int i,j,first, entrytmp;
 for (i=0; i<filelist_size; i++) {
  first=i;
  for (j=i+1; j<filelist_size; j++) {
   if ( cmpstr(filelist+filelist_hash[j]*CRSID_PATH_LENGTH_MAX, filelist+filelist_hash[first]*CRSID_PATH_LENGTH_MAX)<0 ) first=j;
  }
  if (first!=i) { entrytmp=filelist_hash[i]; filelist_hash[i]=filelist_hash[first]; filelist_hash[first]=entrytmp; }
 }
}

static void findSIDfiles (char* basefolder, char recursive) {
 int i; DIR *Dir; struct dirent *Entry; char Path[CRSID_PATH_LENGTH_MAX]; static char DirPath[CRSID_PATH_LENGTH_MAX];
 char *FileList; //[DIRENTRIES_MAX][PATH_LENGTH_MAX]; //a preallocated array wouldn't fit on stack
 int FileListSize=0; int FileListHash[CRSID_PLAYLIST_ENTRIES_MAX];

 //process files in folder
 Dir = opendir(basefolder); if (!Dir) return;
 FileList = malloc(DIRENTRIES_MAX*CRSID_PATH_LENGTH_MAX); FileListSize=0;
 while ( (Entry=readdir(Dir)) != NULL && FileListSize < DIRENTRIES_MAX ) {
  strcpy(Path,basefolder); strcat(Path,DIR_SEPARATOR/* "/" */); strcat(Path,Entry->d_name);
  if (isFile(Path) && cRSID_playableExtension(Entry->d_name)) addToFileList( Path, 1, FileList, &FileListSize, FileListHash );
 }
 closedir(Dir);
 if (FileListSize>0) {
  if ( strcmp(basefolder,".") ) {
   strcpy(Path,"---"); strcat(Path,cRSID_fileNameOnly(basefolder)); strcat(Path,"---"); addToPlayList(Path,0, 0,0);
  }
  else addToPlayList("Content of current folder:",0, 0,0);
  sortFileList( FileList, FileListSize, FileListHash );
  for (i=0; i<FileListSize; ++i) { addToPlayList( FileList+FileListHash[i]*CRSID_PATH_LENGTH_MAX, 1, 0,0 ); }
 }
 //process subfolders recursively
 if (recursive) {
  Dir = opendir(basefolder); if (!Dir) { free(FileList); return; }
  FileListSize=0;
  while ( (Entry=readdir(Dir)) != NULL && FileListSize < DIRENTRIES_MAX ) {
   strcpy(Path,basefolder); strcat(Path,DIR_SEPARATOR/* "/" */); strcat(Path,Entry->d_name);
   if ( isDir(Path) && strcmp(Entry->d_name,".") && strcmp(Entry->d_name,"..") ) addToFileList( Path, 0, FileList, &FileListSize, FileListHash );
  }
  closedir(Dir);
  if (FileListSize>0) {
   sortFileList( FileList, FileListSize, FileListHash ); for (i=0; i<FileListSize; ++i) {
    findSIDfiles( FileList+FileListHash[i]*CRSID_PATH_LENGTH_MAX, recursive );
   }
  }
 }
 free(FileList);
}

void findSortSIDfiles (char* basefolder, char recursive) {
 //find the files
 findSIDfiles (basefolder, recursive);
}


void setKeyCapture (char state) {
 #ifdef LINUX
  struct termios TTYstate;
  tcgetattr(STDIN_FILENO, &TTYstate);
  if (state) { TTYstate.c_lflag &= ~( ICANON | ECHO ); fcntl( 0, F_SETFL, fcntl(0,F_GETFL) | O_NONBLOCK ); } //cfmakeraw( &TTYstate ); }  //chmakeraw fucks up line-width
  else { TTYstate.c_lflag |= ( ICANON | ECHO ); }
  tcsetattr(STDIN_FILENO, TCSANOW, &TTYstate);
 #endif
}

static void printUsage() {
 static char WasDisplayed=0;
 if(!WasDisplayed) {
   printf("Usage of cRSID-"CRSID_VERSION" (parameters can follow in any order):\n"
          "crsid  [ -cli | filename(.sid) | -playtime <seconds> | subtunenumber | folder | playlistfile.sil "
          "| -info | -noadvance | -autoexit | -nofadeout | [ -mono | -stereo | -narrow ] | [ -sid6581 | -sid8580 ]"
          "| [ -sidlight | -sidhq ] | [ -resamplehq | -resamplight ] | | -buflen <256..32768> | -volume <0..255> "
          "| -songlengths <filename> | -kernal <filename> | -basic <filename> ] \n");
  if (cRSID.CLImode) printf("\n%s\n",HelpText);
 }
 WasDisplayed=1;
}


static char processListFile (char* filename) {
 static char ListEntry[CRSID_PATH_LENGTH_MAX+CRSID_PLAYTIMESTRING_LENGTH_MAX], Path[CRSID_PATH_LENGTH_MAX]; int SizeTmp, ScannedDataCount, Minutes,Seconds;
 FILE* fp = fopen(filename,"r");
 if (fp!=NULL) {
  SizeTmp = cRSID.PlayListSize; addToPlayList(cRSID_fileNameOnly(filename),0, 0,0);
  while ( fgets (ListEntry, CRSID_PATH_LENGTH_MAX, fp) ) {
   cRSID_removeNewline( ListEntry );
   ScannedDataCount = sscanf( ListEntry, "%s %d:%d", Path, &Minutes, &Seconds );
   if (ScannedDataCount >= 1) {
    if (fileExists(Path) && isFile(Path) ) {
     addToPlayList( Path, 1, ScannedDataCount>=2 ? (ScannedDataCount>=3? Minutes:0) : 0, ScannedDataCount>=2 ? (ScannedDataCount>=3? Seconds:Minutes) : 0 );
    }
    else if (isDir(Path)) { cRSID_removeNewline(Path); findSortSIDfiles(Path,1); }
   }
  }
  fclose(fp); if (cRSID.PlayListSize==SizeTmp+1) { cRSID.PlayListSize=SizeTmp; return 0; } //if nothing was found
  return 1;
 }
 return 0;
}

static char findListFile (char* basefolder) {
 char Path[CRSID_PATH_LENGTH_MAX]; static char DirPath[CRSID_PATH_LENGTH_MAX]; DIR *Dir; struct dirent *Entry;
 Dir = opendir(basefolder); if (!Dir) return 0;
 while ( (Entry=readdir(Dir)) != NULL ) {
  if ( strcmp(Entry->d_name,".") && strcmp(Entry->d_name,"..") ) {
   strcpy(Path,basefolder); strcat(Path,DIR_SEPARATOR/* "/" */); strcat(Path,Entry->d_name);
   if ( cRSID_compareFileExtension(Entry->d_name,PLAYLIST_FILE_EXTENSION) ) {
    closedir(Dir); return processListFile( Path );
 }}}
 closedir(Dir); return 0;
}


static void setFirstPlayableItem (char** sid_filename) {
 *sid_filename = cRSID.PlayList[ cRSID.PlayListPlayPosition = cRSID_findNextPlayableItem(0) ];
}

static void* loadFileIntoMemory (char* filename, int expected_size, char verbose, char optional) {
   char* Address = NULL; //static long FileSize;
   int ReadSize;
   struct stat FileStats;
   if ( stat( filename, &FileStats ) < 0 ) { if(verbose) printf( "Couldn't find%sfile \"%s\" or couldn't determine its size.\n", optional?" optional ":"", filename ); return NULL; } //might not be Windows-compatible?
   if (expected_size && FileStats.st_size < expected_size) { printf( "File \"%s\" is smaller than expected %d bytes. Refusing to load it.\n", filename, expected_size ); return NULL; }
   FILE *File = fopen( filename, "rb" );
   if (File == NULL) { printf( "Couldn't open file %s\n", filename ); return NULL; }
   //fseek( File, 0, SEEK_END ); FileSize = ftell( File ); rewind( File );
   ReadSize = expected_size? expected_size : FileStats.st_size;
   Address = (char*) malloc( /*FileSize*/ /*FileStats.st_size*/ ReadSize + 1 ); if (Address == NULL) { printf( "Couldn't allocate memory for file %s\n", filename ); return NULL; }
   size_t ReadCount = fread( Address, 1, /*FileSize*/ /*FileStats.st_size*/ ReadSize, File );  //printf("%p,%d,%d,$%2.2X,$%2.2X\n",Address,FileStats.st_size,ReadCount,Address[0],Address[ReadCount-1]);
   Address[ ReadCount ] = '\0'; fclose( File ); //adding string termination considering case of a string-file
   return Address;
}

char* makePath (char* folderpath, char* filename) { //don't assing the output to a variable, use it only on demand...
 static char PathAndFileName [CRSID_PATH_LENGTH_MAX+2+CRSID_FILENAME_LEN_MAX+2];
 snprintf( PathAndFileName, CRSID_PATH_LENGTH_MAX+2+CRSID_FILENAME_LEN_MAX, "%s%s%s", folderpath, DIR_SEPARATOR, filename );
 return PathAndFileName; //not preferable in C to return pointer of internal array, but this at least avoids memory-leak
}

static char* addHomeFolderPath (char* basename) { //don't assing the output to a variable, use it only on demand...
 char *HomeFolder = getenv("HOME");
 #ifdef WINDOWS
 if (HomeFolder == NULL) HomeFolder = getenv("USERPROFILE");
 #endif
 return makePath( HomeFolder, basename );
}

static char* getCRSIDfolderPath () { //don't assing the output to a variable, use it only on demand...
 static char Path [CRSID_PATH_LENGTH_MAX+4];
 strncpy( Path, addHomeFolderPath( CRSID_USER_SUBFOLDER ), CRSID_PATH_LENGTH_MAX+2 );
 return Path; //not preferable in C to return pointer of internal array, but this at least avoids memory-leak
}


static void loadSonglengthsFile (char* filename, char verbose) {
 if ( ( cRSID_setSongLengthData( loadFileIntoMemory( filename, 0, verbose, 1 ) ) ) != NULL && cRSID.CLImode )
 { printf( "Songlengths file \"%s\" found and loaded successfully.\n", filename ); }
}

static void loadKERNALfile (char* filename, char verbose) {
 if ( ( cRSID_setKERNALdata( loadFileIntoMemory( filename, 0x2000, verbose, 1 ) ) ) != NULL && cRSID.CLImode )
 { printf( "KERNAL-ROM file \"%s\" found and loaded successfully.\n", filename ); }
}

static void loadBASICfile (char* filename, char verbose) {
 if ( ( cRSID_setBASICdata( loadFileIntoMemory( filename, 0x2000, verbose, 1 ) ) ) != NULL && cRSID.CLImode )
 { printf( "BASIC-ROM file \"%s\" found and loaded successfully.\n", filename ); }
}


static char readConfigFileData (FILE* file, char load) {

 static struct LoadedConfigData {
  char FileDialogDirectory [CRSID_PATH_LENGTH_MAX+2];
  int MainVolume;
  int Stereo;
  int HighQualitySID;
  int HighQualityResampler;
  //int AutoAdvance;
  //int SelectedSIDmodel;
 } LoadedConfigData;

 char Difference = 0;
 //these rows should be kept in sync with saveConfigFile() rows:
 if ( fscanf( file, "FileDialogDirectory=%s\n", (char*) &LoadedConfigData.FileDialogDirectory ) == 1 ) {
  if ( strcmp( FileDialogDirectory, LoadedConfigData.FileDialogDirectory ) ) Difference = 1;
  if (load) strcpy( FileDialogDirectory, LoadedConfigData.FileDialogDirectory );
 }
 if ( fscanf( file, "MainVolume=%d\n", &LoadedConfigData.MainVolume ) == 1 ) {
  if (cRSID.MainVolume != LoadedConfigData.MainVolume) Difference = 1;
  if (load) cRSID.MainVolume = (unsigned char) LoadedConfigData.MainVolume;
 }
 if ( fscanf( file, "Stereo=%d\n", &LoadedConfigData.Stereo ) == CRSID_CHANNELMODE_STEREO ) {
  if (cRSID.Stereo != LoadedConfigData.Stereo) Difference = 1;
  if (load) cRSID.Stereo = (unsigned char) LoadedConfigData.Stereo;
 }
 if ( fscanf( file, "HighQualitySID=%d\n", &LoadedConfigData.HighQualitySID ) == 1 ) {
  if (cRSID.HighQualitySID != LoadedConfigData.HighQualitySID) Difference = 1;
  if (load) cRSID.HighQualitySID = LoadedConfigData.HighQualitySID;
 }
 if ( fscanf( file, "HighQualityResampler=%d\n", &LoadedConfigData.HighQualityResampler ) == 1 ) {
  if (cRSID.HighQualityResampler != LoadedConfigData.HighQualityResampler) Difference = 1;
  if (load) cRSID.HighQualityResampler = LoadedConfigData.HighQualityResampler;
 }
 /*if ( fscanf( file, "AutoAdvance=%d\n", &LoadedConfigData.AutoAdvance ) == 1 ) {
  if (cRSID.AutoAdvance != LoadedConfigData.AutoAdvance) Difference = 1;
  if (load) cRSID.AutoAdvance = (char) LoadedConfigData.AutoAdvance;
 }*/
 return Difference;
}


static void loadConfigFile () {
 FILE *file;
 if ( ( file = fopen( makePath( getCRSIDfolderPath(), CONFIGFILE_NAME ), "r" ) ) == NULL ) {
  printf( "There's no config file saved yet. Will create it upon exit.\n" );
 }
 else { readConfigFileData( file, 1 ); fclose( file ); }
}


static void saveConfigFile () {
 FILE *file; char Difference = 0;
 if ( ( file = fopen( makePath( getCRSIDfolderPath(), CONFIGFILE_NAME ), "r" ) ) == NULL )
 { printf( "Couldn't open a config-file yet for change-checking! Trying to save it anyway (for the first time)...\n" ); }
 else { Difference = readConfigFileData( file, 0 ); fclose( file ); if ( !Difference ) return; }
 if ( ( file = fopen( makePath( getCRSIDfolderPath(), CONFIGFILE_NAME ), "w" ) ) != NULL ) {
  //these rows should be kept in sync with readConfigFiledata() rows:
  fprintf( file, "FileDialogDirectory=%s\n", FileDialogDirectory );
  fprintf( file, "MainVolume=%d\n", cRSID.MainVolume );
  fprintf( file, "Stereo=%d\n", cRSID.Stereo );
  fprintf( file, "HighQualitySID=%d\n", cRSID.HighQualitySID );
  fprintf( file, "HighQualityResampler=%d\n", cRSID.HighQualityResampler );
  //fprintf( file, "AutoAdvance=%d\n", cRSID.AutoAdvance );
  fclose( file );
 }
 else { printf( "Couldn't write the config-file, current settings got lost on exit.\n" ); }
}


//======================================================================================


//Some GUI.c stuff Moved here for reach by CLI-only crsid without the need to include anything from GUI folder:

byte FirstDraw=1;
char FileDialogDirectory [CRSID_PATH_LENGTH_MAX+2] = ".";


/*static*/ void startNextPlayListItem () {
 if (!cRSID.OpenedMusic) cRSID_nextTune(); else cRSID_startPlayListItem( cRSID.PlayListPlayPosition );
 cRSID.OpenedMusic=0;
 FirstDraw=1; //drawOnce(C64);
}
/*static*/ void startPrevPlayListItem () {
 if (!cRSID.OpenedMusic) cRSID_prevTune(); else cRSID_startPlayListItem( cRSID.PlayListPlayPosition );
 cRSID.OpenedMusic=0;
 FirstDraw=1; //drawOnce(C64);
}

char prevTuneButton() {
 if (cRSID.SubTune > 1) { cRSID_prevSubtune(); FirstDraw=1; return 1; }
 else if (cRSID.PlayListSize>1) startPrevPlayListItem();
 return 0;
}
char nextTuneButton() {
 if (cRSID.SubTune<cRSID.SIDheader->SubtuneAmount) {cRSID_nextSubtune(); FirstDraw=1; return 1; }
 else if (cRSID.PlayListSize>1) startNextPlayListItem();
 return 0;
}

void volumeUpButton() {
 if (cRSID.MainVolume+32<255) cRSID.MainVolume+=32; else cRSID.MainVolume=255;
}
void volumeDownButton() {
 if (cRSID.MainVolume-32>0) cRSID.MainVolume-=32; else cRSID.MainVolume=0;
}


//void printTuneInfo (char subtunestepping, short* prevframecycles) {
void printTuneInfo_callBack (char subtunestepping, void* data) {
 int* prevframecycles = (int*) data;

 cRSID_Interface *C64 = &cRSID; //cRSID_C64instance *C64 = &cRSID_C64;
 cRSID_SIDheader *SIDheader = cRSID.SIDheader;

 if (!subtunestepping) {
  if (cRSID.PlayListSize > 1)
   printf("\n------------------------------ PlayList-item %d -------------------------------------\n",
           cRSID.PlayListNumbering[cRSID.PlayListPlayPosition]);

  printf("Author: %s , Title: %s , Info: %s\n",
         SIDheader->Author, SIDheader->Title, SIDheader->ReleaseInfo);

  printf("Load-address:$%4.4X, End-address:$%4.4X, Size:%d bytes\n", C64->LoadAddress, C64->EndAddress, C64->EndAddress - C64->LoadAddress);
  printf("Init-address:$%4.4X, ", C64->InitAddress);
  if (!C64->RealSIDmode) {
   printf("Play-address:$%4.4X, ", C64->PlayAddress);
   if (SIDheader->PlayAddressH==0 && SIDheader->PlayAddressL==0) printf("(IRQ), ");
  }
 }

 printf("Subtune:%d (of %d)", C64->SubTune, SIDheader->SubtuneAmount);
 if (C64->RealSIDmode) printf(", RealSID");
 else if (C64->PSIDdigiMode) printf(", PSID-digi");
 if (cRSID.SubtuneDurations[cRSID.SubTune]) {
  printf(" (PlayTime: %2.2d:%2.2d)", cRSID.SubtuneDurations[cRSID.SubTune] / 60,
                                     cRSID.SubtuneDurations[cRSID.SubTune] % 60 );
 }
 printf("\n");


 if (!subtunestepping) {
  printf("SID1:$%4.4X,%d(%c) ", cRSID_getSIDbase(1), cRSID_getSIDmodel(1), ChannelChar[cRSID_getSIDchannel(1)]);
  if (cRSID_getSIDbase(2)) printf("SID2:$%4.4X,%d(%c) ", cRSID_getSIDbase(2), cRSID_getSIDmodel(2), ChannelChar[cRSID_getSIDchannel(2)]);
  if (cRSID_getSIDbase(3)) printf("SID3:$%4.4X,%d(%c) ", cRSID_getSIDbase(3), cRSID_getSIDmodel(3), ChannelChar[cRSID_getSIDchannel(3)]);
  if (cRSID_getSIDbase(4)) printf("SID4:$%4.4X,%d(%c) ", cRSID_getSIDbase(4), cRSID_getSIDmodel(4), ChannelChar[cRSID_getSIDchannel(4)]);
  printf("\n");

  *prevframecycles = C64->FrameCycles;
  if (!C64->RealSIDmode) {
   printf( "Speed: %.1fx (player-call at every %d cycle) TimerSource:%s ",
           (C64->VideoStandard<=1? 19656.0:17095.0) / C64->FrameCycles, C64->FrameCycles, C64->TimerSource? "CIA":"VIC" );
  }

  printf ("Standard:%s\n", C64->VideoStandard? "PAL":"NTSC" );
 }

}


