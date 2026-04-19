//anti-aliased GUI library by Hermit (for SDL)

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
//#include <stdint.h>
//#include <unistd.h>
#include <dirent.h>
#include <sys/stat.h>

#include <SDL.h>

#include "GUI.h"


#ifdef WINDOWS
 #define realpath(new,rel) _fullpath((rel),(new),CRSID_PATH_LENGTH_MAX)
#endif


enum Defaults { WINWIDTH=320, PLAYER_WIDTH=WINWIDTH,
 #if (BOTTOM_PLAYLIST)
  PLAYER_HEIGHT=265, PLAYLIST_ROWS=10,
 #else
  PLAYER_HEIGHT=273, PLAYLIST_ROWS=17,
 #endif
                PAGEUP_ROWS=PLAYLIST_ROWS-1, SCROLLSTEPS=5, //WINHEIGHT=240,
                BYTES_PER_PIXEL=4, LOG2_BYTES_PER_PIXEL=2, BITS_PER_PIXEL=(BYTES_PER_PIXEL*8),
                ERRORCODE_DEFAULT=-1, FILELIST_ROWS=12
              };


#include "layout.c"


static char *mouse_xpm[] = {
"32 32 3 1 0 0",
"0	c #000000",
"1	c #FFFFFF",
" 	c None",
"00                              ",
"010                             ",
"01100                           ",
"011100                          ",
"0111100                         ",
"01110100                        ",
"010111100                       ",
"0111111100                      ",
"01111111100                     ",
"01111111100                     ",
"01111111100                     ",
"01111111000                     ",
"0011111000                      ",
" 00000100                       ",
"  000010                        ",
"      010                       ",
"      010                       ",
"       01                       ",
"         1                      ",
"                                ",
"                                ",
"                                ",
"                                ",
"                                ",
"                                ",
"                                ",
"                                ",
"                                ",
"                                ",
"                                ",
"                                ",
"                                "
};

#include "graphics/cRSID-icon-16x16.xpm"


// SDL interprets each pixel as a 32-bit number, so our masks must depend on the endianness (byte order) of the machine
#if SDL_BYTEORDER == SDL_BIG_ENDIAN
 Uint32 rmask = 0xff000000, gmask = 0x00ff0000, bmask = 0x0000ff00, amask = 0x000000ff;
#else
 Uint32 rmask = 0x000000ff, gmask = 0x0000ff00, bmask = 0x00ff0000, amask = 0xff000000;
#endif

static unsigned char icon_pixels[16*16*32]; static SDL_Surface *cRSID_Icon;
static SDL_Cursor *MouseCursor;
static SDL_Event Event;
static char ExitSignal=0;
//static byte FirstDraw=1; //moved outside of GUI folder
//static short WinHeight = PLAYER_HEIGHT;

static char AppRunDirectory [CRSID_PATH_LENGTH_MAX+2] = "";
//char FileDialogDirectory [CRSID_PATH_LENGTH_MAX+2] = "."; //moved outside of GUI folder
static char FirstFileDialog = 1;
static int PreviousFileListDisplayPosition = -1, PreviousFileListCursorPosition = -1;


static void XPMtoPixels(char* source[], unsigned char* target) {
 int i,j,k,sourceindex,targetindex,width,height,colours,charpix,transpchar=0xff;
 unsigned int colr[256],colg[256],colb[256];
 unsigned char colchar[256], colcode,colindex;
 sscanf(source[0],"%d %d %d %d",&width,&height,&colours,&charpix);
 for (i=0;i<colours;i++) {
  if (sscanf(source[1+i],"%c%*s #%2X%2X%2X",&colchar[i],&colr[i],&colg[i],&colb[i])!=4) transpchar=colchar[i];
 }
 for (i=0;i<height;i++) {
  for (j=0;j<width;j++) {
   sourceindex=(i*width+j); colcode=source[colours+1+i][j]; for(k=0;k<colours;k++) if (colcode==colchar[k]) break; colindex=k;
   targetindex=(i*width+j)*4;
   target[targetindex+0] = colr[colindex]; //Red
   target[targetindex+1] = colg[colindex]; //Green
   target[targetindex+2] = colb[colindex]; //Blue
   target[targetindex+3] = (colcode==transpchar)?0x00:0xff ;//Aplha - 0:fully transparent...0xFF:fully opaque
 }}
}


static SDL_Cursor *init_system_cursor(char *image[]) { // Create a new SDL mouse-cursor from an XPM
 int i, row, col; Uint8 data[4*32]; Uint8 mask[4*32]; int hot_x, hot_y;
 for ( i=-1, row=0; row<32; ++row ) {
  for ( col=0; col<32; ++col ) {
   if ( col % 8 ) { data[i] <<= 1; mask[i] <<= 1; }
   else { ++i; data[i] = mask[i] = 0; }
   switch (image[4+row][col]) {
    case '0': data[i] |= 0x01; mask[i] |= 0x01; break;
    case '1': mask[i] |= 0x01; break;
    case ' ': break;
 }}}
 hot_x=hot_y=0; //sscanf(image[4+row], "%d,%d", &hot_x, &hot_y);
 return SDL_CreateCursor(data, mask, 32, 32, hot_x, hot_y);
}


void initGUI() {
 if ( SDL_Init(SDL_INIT_VIDEO) < 0 ) {
  fprintf(stderr, "Couldn't initialize SDL-Video: %s\n",SDL_GetError()); exit(-1);
 }

 SDL_WM_SetCaption("cRSID-"CRSID_VERSION" SID-music player","cRSID-"CRSID_VERSION);

 XPMtoPixels(cRSID_xpm, icon_pixels);
 cRSID_Icon = SDL_CreateRGBSurfaceFrom( (void*)icon_pixels, 16, 16, 32, 16*4, rmask, gmask, bmask, amask );
 SDL_WM_SetIcon(cRSID_Icon, NULL);

 MouseCursor = init_system_cursor(mouse_xpm); SDL_SetCursor(MouseCursor);

 #if (BOTTOM_PLAYLIST)
  WinHeight = PLAYER_HEIGHT + ( (cRSID.PlayListSize>1 || cRSID.BuiltInMusic) ? PLAYLIST_HEIGHT:0 );
 #else
  WinWidth = PLAYER_WIDTH + ( (cRSID.PlayListSize>1 || cRSID.BuiltInMusic) ? PLAYLIST_WIDTH:0 );
 #endif
 Screen = SDL_SetVideoMode( WinWidth/*WINWIDTH*/, WinHeight/*WINHEIGHT*/, BITS_PER_PIXEL, SDL_SWSURFACE); //|SDL_RESIZABLE|SDL_HWACCEL|SDL_ANYFORMAT); //SDL_HWSURFACE|SDL_HWACCEL|SDL_ANYFORMAT); //SDL_OPENGL|SDL_OPENGLBLIT|SDL_DOUBLEBUF|SDL_FULLSCREEN|SDL_SWSURFACE|SDL_RESIZABLE);
 if(Screen==NULL) { printf("Couldn't create SDL window: %s",SDL_GetError()); exit(ERRORCODE_DEFAULT); }

 SDL_EnableKeyRepeat(250,40);
 cRSID.PlayListDisplayPosition = 0; //cRSID.PlayListPlayPosition = 0;
}


void drawGUI () {
 static word angle=0;
 Pixels = Screen->pixels; //WinWidth=Screen->w; WinHeight=Screen->h;

 if (FirstDraw) { FirstDraw=0; drawOnce(); SDL_UpdateRect( Screen, 0, 0, WINWIDTH, PLAYER_HEIGHT /*SID1INFO_Y*/ ); drawPlayList(0); } //SDL_FillRect(Screen, &Screen->clip_rect, MARGIN_COLOR);

 drawRepeatedly(); //SDL_UpdateRect( Screen, 0, FRAMESPEEDINFO_Y, WINWIDTH, PLAYER_HEIGHT/*WINHEIGHT*/-FRAMESPEEDINFO_Y );

 //SDL_UpdateRect( Screen, 0, 0, WINWIDTH, WINHEIGHT ); //WinWidth, WinHeight );
}


//static void startSubTune (byte subtune) { cRSID_startSubtune(subtune); FirstDraw=1; }


//moved outside of GUI folder:

/*static void startNextPlayListItem () {
 if (!cRSID.OpenedMusic) cRSID_nextTune(); else cRSID_startPlayListItem( cRSID.PlayListPlayPosition );
 cRSID.OpenedMusic=0;
 FirstDraw=1; //drawOnce(C64);
}*/
/*static void startPrevPlayListItem () {
 if (!cRSID.OpenedMusic) cRSID_prevTune(); else cRSID_startPlayListItem( cRSID.PlayListPlayPosition );
 cRSID.OpenedMusic=0;
 FirstDraw=1; //drawOnce(C64);
}*/

/*char prevTuneButton() {
 if (cRSID.SubTune > 1) { cRSID_prevSubtune(); FirstDraw=1; return 1; }
 else if (cRSID.PlayListSize>1) startPrevPlayListItem();
 return 0;
}
char nextTuneButton() {
 if (cRSID.SubTune<cRSID.SIDheader->SubtuneAmount) {cRSID_nextSubtune(); FirstDraw=1; return 1; }
 else if (cRSID.PlayListSize>1) startNextPlayListItem();
 return 0;
}*/

/*void volumeUpButton() {
 if (cRSID.MainVolume+32<255) cRSID.MainVolume+=32; else cRSID.MainVolume=255;
}
void volumeDownButton() {
 if (cRSID.MainVolume-32>0) cRSID.MainVolume-=32; else cRSID.MainVolume=0;
}*/


static void playListDown () {
 if(cRSID.PlayListDisplayPosition<cRSID.PlayListSize-PLAYLIST_ROWS) {++cRSID.PlayListDisplayPosition; } //drawPlayList();}
}

static void playListUp () {
 if(cRSID.PlayListDisplayPosition>0) {--cRSID.PlayListDisplayPosition; } //drawPlayList();}
}

static void playListEnd () {
 if (cRSID.PlayListSize > PLAYLIST_ROWS && cRSID.PlayListDisplayPosition < cRSID.PlayListSize-PLAYLIST_ROWS)
 { cRSID.PlayListDisplayPosition = cRSID.PlayListSize-PLAYLIST_ROWS; } //drawPlayList(); }
}

static void startPlayListItem (unsigned int itemnumber) {
 cRSID_startPlayListItem(itemnumber); FirstDraw=1; //drawOnce(C64);
}

static void startSubTune (byte subtune) {
 cRSID_pauseSIDtune();
 cRSID_processSIDfileData( (unsigned char*) cRSID.SIDfileData, cRSID.SIDfileSize ); //needed for tunes irreversibly modifying their memory-content (Dane tunes, SounDemon digis, etc)
 cRSID_initSIDtune( cRSID.SIDheader, subtune ); //cRSID.PlaybackSpeed=1; cRSID.Paused=0;
 cRSID_playSIDtune();
 FirstDraw=1;
}


static INLINE int cmpstr (char *string1, char *string2) { //compare strings alphabetically for sorting
 static unsigned char char1, char2;
 for (;;) {
  char1 = tolower(*string1++); char2 = tolower(*string2++);
  if (char1 < char2) return -1;
  if (char1 > char2) return 1;
  if ((!char1) || (!char2)) return 0;
}}

static INLINE void swapStrings (char *string1, char *string2) {
 static int i; static char Tmp, EndCount;
 for (i=EndCount=0; EndCount<3 && i < FILELIST_ENTRY_SIZE; ++i) {
  Tmp = string1[i]; if (Tmp=='\0') EndCount |= 1;
  if (string2[i]=='\0') EndCount |=2;
  string1[i] = string2[i]; string2[i] = Tmp;
}}


static DIR* readDirectory (char* parent_folder, char* subfolder, char* extension) {
 static int i,j,first;
 static char path[CRSID_PATH_LENGTH_MAX]="";
 DIR *Directory; struct dirent *DirEntry; struct stat st;
 static int TypeTmp; //static direntry entrytmp;

 FileListDisplayPosition=FileListCursorPosition=0; //DirDisplayPos=FileCursorPos=0;
 getcwd( AppRunDirectory, CRSID_PATH_LENGTH_MAX );
 if (parent_folder != NULL) { cRSID.ChangingDirectory = 1; chdir( parent_folder ); }
 getcwd( path, CRSID_PATH_LENGTH_MAX ); chdir( subfolder ); getcwd( FileDialogDirectory, CRSID_PATH_LENGTH_MAX );  // strncpy( FileDialogDirectory, path, CRSID_PATH_LENGTH_MAX ); //getcwd( FileDialogDirectory, CRSID_PATH_LENGTH_MAX );
 Directory = opendir("."); if (Directory==NULL) return NULL; //opendir( path ); //;
 FileListSize=0; //counts entries

 while ( (DirEntry = readdir(Directory)) != NULL ) {
  stat(DirEntry->d_name, &st);
  if (st.st_mode & S_IFDIR) FileTypes[FileListSize]=0;
  else FileTypes[FileListSize]=1;
  if ( (!FileTypes[FileListSize] && strcmp(DirEntry->d_name,".")) || cRSID_compareFileExtension(DirEntry->d_name,extension) ) {
   strncpy( FileList[FileListSize], DirEntry->d_name, FILELIST_ENTRY_SIZE );
   FileListSize++;
 }}
 closedir(Directory);
 chdir( AppRunDirectory ); cRSID.ChangingDirectory = 0;

 for (i=0; i<FileListSize; i++) { //sort filenames (and types) alphabetically
  first=i;
  for (j=i+1; j<FileListSize; j++) {
   if ( FileTypes[j] < FileTypes[first]
   || ( FileTypes[j] == FileTypes[first] && cmpstr( FileList[j], FileList[first] ) < 0 ) ) {
    first=j;
  }}
  if (first!=i) {
   TypeTmp=FileTypes[i]; FileTypes[i]=FileTypes[first]; FileTypes[first]=TypeTmp;
   swapStrings( FileList[i], FileList[first] );
  }
 }

 return Directory;
}


static void fileListUp () {
 if (FileListCursorPosition > 0) {
  --FileListCursorPosition;
  if ( FileListCursorPosition <= FileListDisplayPosition+2 && FileListDisplayPosition > 0) --FileListDisplayPosition;
 }
}

static void fileListDown () {
 if ( FileListCursorPosition < FileListSize - 1 ) {
  ++FileListCursorPosition;
  if ( FileListCursorPosition >= FileListDisplayPosition+FILELIST_ROWS-3
      && FileListDisplayPosition+FILELIST_ROWS < FileListSize ) ++FileListDisplayPosition;
 }
}


static void openFileDialog () {
 //getcwd( AppRunDirectory, CRSID_PATH_LENGTH_MAX );
 //chdir( FileDialogDirectory );
 /*if (FirstFileDialog)*/ { FirstFileDialog=0; readDirectory( NULL, FileDialogDirectory, CRSID_SID_FILE_EXTENSION ); }
 if (0 <= PreviousFileListDisplayPosition && PreviousFileListDisplayPosition <= FileListSize-FILELIST_ROWS)
 { FileListDisplayPosition = PreviousFileListDisplayPosition; }
 if (0 <= PreviousFileListCursorPosition && PreviousFileListCursorPosition < FileListSize && PreviousFileListCursorPosition < FileListDisplayPosition+FILELIST_ROWS)
 { FileListCursorPosition = PreviousFileListCursorPosition; }
 MenuMode=1; FirstDraw=1; drawGUI();
}
static void closeFileDialog () {
 MenuMode=0; FirstDraw=1; drawGUI();
 PreviousFileListDisplayPosition = FileListDisplayPosition;
 PreviousFileListCursorPosition = FileListCursorPosition;
 //chdir( AppRunDirectory );
}

static void changeDirectory (int index) {
 //chdir ( FileDialogDirectory ); //chdir( FileList[index] );
 readDirectory( FileDialogDirectory, FileList[index], CRSID_SID_FILE_EXTENSION );
 chdir( AppRunDirectory ); cRSID.ChangingDirectory = 0;
 FileListDisplayPosition = FileListCursorPosition = 0; drawFileList();
}


static void openFile () {
 //static char path [CRSID_PATH_LENGTH_MAX+2] = "";
 cRSID.ChangingDirectory = 1; chdir( FileDialogDirectory );
 if (cRSID.BuiltInMusic) { cRSID.PlayListPlayPosition = 1; cRSID.PlayListNumbering[0]=0; strcpy( cRSID.PlayList[0], "New playlist:" ); }
 cRSID.BuiltInMusic = 0;
 cRSID_playSIDfile( FileList[FileListCursorPosition], 1 ); cRSID.OpenedMusic=1; //cRSID.AutoAdvance=0;
 chdir ( AppRunDirectory ); cRSID.ChangingDirectory = 0;
 closeFileDialog(); //FirstDraw=1; //drawOnce(C64);
}

static void addFile () {
 char *RelativePath=NULL, *AbsolutePath=NULL; //static char path [CRSID_PATH_LENGTH_MAX+2] = "";
 if ( !FileTypes[FileListCursorPosition] ) return; //if ( !strcpy( FileList[FileListCursorPosition], ".." ) || !strcpy( FileList[FileListCursorPosition], "." ) ) return;

 if (cRSID.PlayListSize<=1 &&
  #if (BOTTOM_PLAYLIST)
   WinHeight == PLAYER_HEIGHT
  #else
   WinWidth == PLAYER_WIDTH
  #endif
  ) { //initGUI();

  #if (BOTTOM_PLAYLIST)
   WinHeight = PLAYER_HEIGHT + PLAYLIST_HEIGHT;
  #else
   WinWidth = PLAYER_WIDTH + PLAYLIST_WIDTH;
  #endif
  Screen = SDL_SetVideoMode( WinWidth/*WINWIDTH*/, WinHeight/*WINHEIGHT*/, BITS_PER_PIXEL, SDL_SWSURFACE); //|SDL_RESIZABLE|SDL_HWACCEL|SDL_ANYFORMAT); //SDL_HWSURFACE|SDL_HWACCEL|SDL_ANYFORMAT); //SDL_OPENGL|SDL_OPENGLBLIT|SDL_DOUBLEBUF|SDL_FULLSCREEN|SDL_SWSURFACE|SDL_RESIZABLE);
  if(Screen==NULL) { printf("Couldn't resize SDL window for playlist: %s",SDL_GetError()); exit(ERRORCODE_DEFAULT); }
  Pixels = Screen->pixels; cRSID.PlayListDisplayPosition = 0; //cRSID.PlayListPlayPosition = 0;
  FirstDraw = 1; //drawGUI(); //drawOnce();  //will draw playlist too
 }
 RelativePath = makePath( FileDialogDirectory, FileList[FileListCursorPosition] );  //chdir( FileDialogDirectory );
 if ( ( AbsolutePath = realpath( RelativePath, NULL ) ) != NULL ) {
  addToPlayList( AbsolutePath, 1, 0,0 ); //addToPlayList( makePath( FileDialogDirectory, FileList[FileListCursorPosition] ), 1, 0,0 );
  free( AbsolutePath );  //chdir ( AppRunDirectory );
 }
 else {
  printf( "Couldn't find absolute-path of relative-path \"%s\", so adding relative-path to playlist instead.\n", RelativePath ); //return;
  addToPlayList( RelativePath, 1, 0,0 );
 }
 //if (cRSID.PlayListSize > 1) {
  if (cRSID.BuiltInMusic && cRSID.PlayListSize==2) {
   cRSID.BuiltInMusic = 0; cRSID.PlayListPlayPosition = 1;
   cRSID.PlayListNumbering[0]=0; strcpy( cRSID.PlayList[0], "New playlist:" );
  }
 //}
 playListEnd(); drawPlayList( !FirstDraw || cRSID.PlayListSize < 2 );
}


static void deleteLastPlaylistItem () {
 if (cRSID.PlayListSize > 0 &&
 #if (BOTTOM_PLAYLIST)
  WinHeight > PLAYER_HEIGHT
 #else
  WinWidth > PLAYER_WIDTH
 #endif
  ) {

  playListEnd();
  if (cRSID.PlayListNumbering[cRSID.PlayListSize-1] && cRSID.PlayListNumber > 1) --cRSID.PlayListNumber;
  --cRSID.PlayListSize;
  if (cRSID.PlayListSize >= PLAYLIST_ROWS && cRSID.PlayListDisplayPosition >= cRSID.PlayListSize-PLAYLIST_ROWS)
  { cRSID.PlayListDisplayPosition = cRSID.PlayListSize-PLAYLIST_ROWS; }
  if (cRSID.PlayListPlayPosition >= cRSID.PlayListSize) cRSID.PlayListPlayPosition = cRSID.PlayListSize - 1;
 }
 else { cRSID.PlayListDisplayPosition = 0; cRSID.PlayListPlayPosition = 0; }
 drawPlayList(1); //FirstDraw=1;
}


static int savePlaylist () {
 int i; char* Path; FILE* file;
 Path = makePath( cRSIDfolderName, DEFAULT_PLAYLIST_FILENAME );
 if ( ( file = fopen( Path, "w" ) ) == NULL ) { printf("Couldn't save default playlist-file \"%s\"\n", Path ); return -1; }
 for (i=0; i < cRSID.PlayListSize; ++i) if (cRSID.PlayListNumbering[i]) fprintf( file, "%s\r\n", cRSID.PlayList[i] );
 fclose( file ); //SaveButtonState = 0;
 return 0;
}


static void changeQuality () {
 if (cRSID.HighQualitySID) {
  if (1/*cRSID.HighQualityResampler*/) { cRSID.HighQualitySID=0; cRSID.HighQualityResampler=0; }
  else { cRSID.HighQualityResampler = 1; }
 }
 else { cRSID.HighQualitySID = 1; cRSID.HighQualityResampler=0; }
}



void mainLoop () {
 //static cRSID_C64instance* C64 = &cRSID_C64;

 enum Periods { INPUTSCAN_PERIOD=10, FRAMETIME=40, //ms
                SCREENUPDATE_PERIOD = (FRAMETIME/INPUTSCAN_PERIOD) };

 static short i, ScreenUpdateCounter=0, PrevPressedButton=0; static const SDL_VideoInfo *VideoInfo;
 static Uint8* KeyStates; static int Tmp, MouseX, MouseY; static Uint32 MouseButtonStates;
 static char ModKeys=0, ScrollBarGrabbed=0, PrevScrollBarGrabbed=0, FilerScrollBarGrabbed=0, PrevFilerScrollBarGrabbed=0;


 while(!ExitSignal) {

  KeyStates = SDL_GetKeyState(NULL);
  ModKeys = KeyStates[SDLK_LSHIFT] || KeyStates[SDLK_RSHIFT] || KeyStates[SDLK_LCTRL] || KeyStates[SDLK_RCTRL] || KeyStates[SDLK_LALT];

  while (SDL_PollEvent(&Event)) {
   //MenuButtonState = PlaybackButtonState = 0;
   OpenButtonState = AddButtonState = DelButtonState = SaveButtonState = BackButtonState = 0;
   ChannelsButtonState = QualityButtonState = 0;
   if (Event.type == SDL_QUIT) { ExitSignal=1; }
   else if(Event.type==SDL_KEYDOWN) {
    switch (Event.key.keysym.sym) {
     case SDLK_ESCAPE: if (MenuMode) { closeFileDialog(); } else ExitSignal=1; break;
     case SDLK_SPACE: cRSID.PlaybackSpeed=1; cRSID.Paused^=1; if(cRSID.Paused) cRSID_pauseSIDtune(); else cRSID_playSIDtune(); break;
     case SDLK_1: startSubTune(1); break;
     case SDLK_2: startSubTune(2); break;
     case SDLK_3: startSubTune(3); break;
     case SDLK_4: startSubTune(4); break;
     case SDLK_5: startSubTune(5); break;
     case SDLK_6: startSubTune(6); break;
     case SDLK_7: startSubTune(7); break;
     case SDLK_8: startSubTune(8); break;
     case SDLK_9: startSubTune(9); break;
     case SDLK_m: case SDLK_o: case SDLK_a: case SDLK_f:
      if (!MenuMode) { /*if (ModKeys)*/ openFileDialog(); }
      break;
     case SDLK_p:
      if (MenuMode) { /*if (ModKeys)*/ closeFileDialog(); }
      break;
     case SDLK_s: /*if (MenuMode)*/ if (ModKeys) savePlaylist();
      break;
     case SDLK_q: /*if (MenuMode)*/ changeQuality(); /*if (MenuMode)*/ checkDrawMenuButtons(1);
      break;
     case SDLK_RETURN:
      if (!MenuMode) {
       /*if (!ModKeys) {
        cRSID_processSIDfileData( cRSID.SIDfileData, cRSID.SIDfileSize );
        cRSID_initSIDtune( cRSID.SIDheader, cRSID.SubTune );
       }
       else*/ startSubTune( cRSID.SubTune );
      }
      else { //change directory or add file to playlist //{ MenuMode=0; FirstDraw=1; drawGUI(); }
       if ( !FileTypes[FileListCursorPosition] ) changeDirectory( FileListCursorPosition );
       else { if (ModKeys) addFile(); else openFile(); }
      }
      break; //PressedButton=1; break;
     case SDLK_PLUS: case SDLK_KP_PLUS: case SDLK_EQUALS: cRSID_nextSubtune(); FirstDraw=1; break; //startSubTune(++cRSID.SubTune); break;
     case SDLK_MINUS: case SDLK_KP_MINUS: cRSID_prevSubtune(); FirstDraw=1; break; //startSubTune(--cRSID.SubTune); break;
     case SDLK_UP:
      if (ModKeys) { playListUp(); drawPlayList(0); }
      else {
       if (!MenuMode) { volumeUpButton(); break; } //if (cRSID.MainVolume+32<255) cRSID.MainVolume+=32; else cRSID.MainVolume=255; break;
       else { fileListUp(); drawFileList(); }
      }
      break;
     case SDLK_DOWN:
      if (ModKeys) { playListDown(); drawPlayList(0); }
      else {
       if (!MenuMode) { volumeDownButton(); break; } //if (cRSID.MainVolume-32>0) cRSID.MainVolume-=32; else cRSID.MainVolume=0; break;
       else { fileListDown(); drawFileList(); }
      }
      break;
     case SDLK_PAGEDOWN:
      if (ModKeys) { playListDown(); drawPlayList(0); }
      else {
       if (!MenuMode) { for(i=0;i<PAGEUP_ROWS;++i) playListDown(); drawPlayList(0); break; }
       else { for(i=0;i<PAGEUP_ROWS;++i) {fileListDown();}  drawFileList(); }
      }
      break;
     case SDLK_PAGEUP:
      if (ModKeys) { playListUp(); drawPlayList(0); }
      else {
       if (!MenuMode) { for(i=0;i<PAGEUP_ROWS;++i) playListUp(); drawPlayList(0); break; }
       else { for(i=0;i<PAGEUP_ROWS;++i) {fileListUp();}  drawFileList(); }
      }
      break;
     case SDLK_INSERT: playListUp(); drawPlayList(0); break;
     case SDLK_DELETE: playListDown(); drawPlayList(0); break;
     case SDLK_HOME:
      if (!MenuMode) {
       cRSID.PlayListDisplayPosition=0; drawPlayList(0); break;
      }
      else { FileListDisplayPosition = FileListCursorPosition = 0; drawFileList(); } //filelist home
      break;
     case SDLK_END:
      if (!MenuMode) {
       playListEnd(); drawPlayList(0); break;
      }
      else { //filelist end
       if (FileListSize >= FileListDisplayPosition+FILELIST_ROWS) FileListDisplayPosition = FileListSize-FILELIST_ROWS;
       FileListCursorPosition = FileListSize-1; drawFileList();
      }
      break;
     case SDLK_LEFT: if (ModKeys) { if (cRSID.PlayListSize>1) startPrevPlayListItem(); }
                     else prevTuneButton();
                     break;
     case SDLK_RIGHT: if (ModKeys) { if (cRSID.PlayListSize>1) startNextPlayListItem(); }
                      else nextTuneButton();
                      break;
     default: break;
    }
   }
   else if (Event.type == SDL_MOUSEBUTTONDOWN) {
    if (Event.button.button==SDL_BUTTON_LEFT) {
     if ( /*!MenuMode &&*/ MENUBUTTON_Y <= Event.button.y && Event.button.y < MENUBUTTON_Y+MENUBUTTON_HEIGHT && Event.button.x < PLAYER_WIDTH ) {
      if (MENUBUTTON_X <= Event.button.x && Event.button.x < MENUBUTTON_X+MENUBUTTON_WIDTH) {
       MenuButtonState = 1; openFileDialog();
      }
      if (PLAYBACK_BUTTON_X <= Event.button.x && Event.button.x < PLAYBACK_BUTTON_X+MENUBUTTON_WIDTH) {
       PlaybackButtonState = 1; closeFileDialog();
      }
      else if (CHANNELS_BUTTON_X<=Event.button.x && Event.button.x < CHANNELS_BUTTON_X+CHANNELS_BUTTON_WIDTH) {
       ChannelsButtonState = 1;
       cRSID.Stereo = cRSID.Stereo == CRSID_CHANNELMODE_MONO ? CRSID_CHANNELMODE_NARROW :
                                       (cRSID.Stereo == CRSID_CHANNELMODE_STEREO ? CRSID_CHANNELMODE_MONO : CRSID_CHANNELMODE_STEREO);  //cRSID.Stereo ^= 1;
      }
      else if (QUALITY_BUTTON_X<=Event.button.x && Event.button.x < QUALITY_BUTTON_X+QUALITY_BUTTON_WIDTH) {
       QualityButtonState = 1; changeQuality();
      }
     }
     else if (!MenuMode && BUTTONS_Y <= Event.button.y && Event.button.y < BUTTONS_Y+PUSHBUTTON_HEIGHT && Event.button.x < PLAYER_WIDTH) {
      for(i=0;i<PUSHBUTTON_AMOUNT;++i)
       if (PushButtonX[i]<=Event.button.x && Event.button.x < PushButtonX[i]+PUSHBUTTON_WIDTH) { PressedButton=i+1; break; }
     }
     else if (PLAYLIST_ELEMENTS_Y < Event.button.y && Event.button.y < PLAYLIST_Y+PLAYLIST_HEIGHT-(PLAYLIST_HEIGHT%ELEMENT_HEIGHT)-1 && Event.button.x >= PLAYLIST_X) {
      if (Event.button.x < PLAYLIST_X + SCROLLBAR_X) {
       startPlayListItem( (Event.button.y-PLAYLIST_ELEMENTS_Y ) / ROWHEIGHT_PLAYLIST + cRSID.PlayListDisplayPosition );
       cRSID.OpenedMusic = 0;
      }
      else { ScrollBarGrabbed=1; }
     }
     else if (MenuMode && Event.button.x < PLAYER_WIDTH) { //File-dialog with playlist buttons
      if (DIALOG_BUTTONS_Y <= Event.button.y && Event.button.y < DIALOG_BUTTONS_Y+DIALOG_BUTTON_HEIGHT) {
       if (OPEN_BUTTON_X<=Event.button.x && Event.button.x < OPEN_BUTTON_X+OPEN_BUTTON_WIDTH) {
        OpenButtonState = 1; openFile();
       }
       else if (ADD_BUTTON_X<=Event.button.x && Event.button.x < ADD_BUTTON_X+ADD_BUTTON_WIDTH) {
        AddButtonState = 1; addFile();
       }
       else if (DEL_BUTTON_X<=Event.button.x && Event.button.x < DEL_BUTTON_X+DEL_BUTTON_WIDTH) {
        DelButtonState = 1; deleteLastPlaylistItem();
       }
       else if (SAVE_BUTTON_X<=Event.button.x && Event.button.x < SAVE_BUTTON_X+SAVE_BUTTON_WIDTH) {
        SaveButtonState = 1; savePlaylist();
       }
       /*else if (QUALITY_BUTTON_X<=Event.button.x && Event.button.x < QUALITY_BUTTON_X+QUALITY_BUTTON_WIDTH) {
        QualityButtonState = 1; changeQuality();
       }*/
       else if (BACK_BUTTON_X<=Event.button.x && Event.button.x < BACK_BUTTON_X+BACK_BUTTON_WIDTH) {
        BackButtonState = 1; closeFileDialog();
       }
      }
      else if (FILELIST_ELEMENTS_Y < Event.button.y && Event.button.y < FILELIST_ELEMENTS_Y+FILELIST_HEIGHT-1) {
       if (Event.button.x < SCROLLBAR_X) { //change directory or add file to playlist //{ MenuMode=0; FirstDraw=1; drawGUI(); }
        Tmp = (Event.button.y-FILELIST_ELEMENTS_Y ) / ROWHEIGHT_FILELIST + FileListDisplayPosition;
        if (Tmp >= FileListSize) break;
        if ( !FileTypes[Tmp] ) changeDirectory( Tmp );
        else {
         if (ModKeys) { FileListCursorPosition = Tmp; drawFileList(); addFile(); }
         else if (FileListCursorPosition != Tmp) { FileListCursorPosition = Tmp; drawFileList(); }
         else openFile();
        }
       }
       else { FilerScrollBarGrabbed=1; }
      }
     }
    }
    else if (Event.button.button==SDL_BUTTON_RIGHT) {
     if (Event.button.y >= PLAYLIST_ELEMENTS_Y && Event.button.x >= PLAYLIST_X+SCROLLBAR_X && Event.button.x >= PLAYLIST_X) {
      for(i=SCROLLSTEPS; i>=0 ; --i) playListUp();
      drawPlayList(0);
     }
     else if (Event.button.y < PLAYER_HEIGHT && Event.button.x < PLAYER_WIDTH) {
      if (Event.button.x >= SCROLLBAR_X) {
       for(i=SCROLLSTEPS; i>=0 ; --i) fileListUp();
       drawFileList();
      }
      else if (FILELIST_ELEMENTS_Y < Event.button.y && Event.button.y < FILELIST_ELEMENTS_Y+FILELIST_HEIGHT-1 && Event.button.x < PLAYER_WIDTH) {
       Tmp = (Event.button.y-FILELIST_ELEMENTS_Y ) / ROWHEIGHT_FILELIST + FileListDisplayPosition;
       if ( Tmp < FileListSize && FileTypes[Tmp] ) { FileListCursorPosition = Tmp; drawFileList(); addFile(); }
      }
     }
    }
    else if (Event.button.button==SDL_BUTTON_WHEELUP) { //else if (Event.button.button==SDL_BUTTON_WHEELUP) { if (cRSID.MainVolume+8<255) cRSID.MainVolume+=8; else cRSID.MainVolume=255; }
     if (Event.button.y<PLAYER_HEIGHT && Event.button.x < PLAYER_WIDTH) {
      if (!MenuMode) { if (cRSID.MainVolume+8<255) cRSID.MainVolume+=8; else cRSID.MainVolume=255; }
      else { for (i=SCROLLSTEPS*(Event.button.x>SCROLLBAR_X); i>=0 ; --i) {fileListUp();}  drawFileList(); }
     }
     else if (Event.button.x >= PLAYLIST_X) { for (i=SCROLLSTEPS*(Event.button.x>PLAYLIST_X+SCROLLBAR_X); i>=0 ; --i) playListUp();  drawPlayList(0); }
    }
    else if (Event.button.button==SDL_BUTTON_WHEELDOWN) { //else if (Event.button.button==SDL_BUTTON_WHEELDOWN) { if (cRSID.MainVolume-8>0) cRSID.MainVolume-=8; else cRSID.MainVolume=0; }
     if (Event.button.y < PLAYER_HEIGHT && Event.button.x < PLAYER_WIDTH) {
      if (!MenuMode) { if (cRSID.MainVolume-8>0) cRSID.MainVolume-=8; else cRSID.MainVolume=0; }
      else { for (i=SCROLLSTEPS*(Event.button.x>SCROLLBAR_X); i>=0 ; --i) {fileListDown();}  drawFileList(); }
     }
     else if (Event.button.x >= PLAYLIST_X) { for (i=SCROLLSTEPS*(Event.button.x>PLAYLIST_X+SCROLLBAR_X); i>=0 ; --i) playListDown();  drawPlayList(0); }
    }
   }

  }


  if (PressedButton && PressedButton!=PrevPressedButton) {
   switch (PressedButton) {
    case 1: startSubTune(cRSID.SubTune); break;
    case 2: cRSID.PlaybackSpeed=1; cRSID.Paused^=1; if(cRSID.Paused) cRSID_pauseSIDtune(); else cRSID_playSIDtune(); break;
    case 4: prevTuneButton(); break; //startSubTune(--cRSID.SubTune); break;
    case 5: nextTuneButton(); break; //startSubTune(++cRSID.SubTune); break;
    //case 6: cRSID.Stereo^=1; break;
    case 6 /*7*/ /*8*/: cRSID.AutoAdvance^=1; break;
    case 7: //6: //7:
     if (ModKeys) { cRSID.SelectedSIDmodel=0; cRSID_setSIDmodels(); break; } //give back the control to SID-header regarding SID-model
     if (cRSID.SelectedSIDmodel==0) cRSID.SelectedSIDmodel=8580;
     else if (cRSID.SelectedSIDmodel==8580) cRSID.SelectedSIDmodel=6581;
     else if (cRSID.SelectedSIDmodel==6581) { cRSID.SelectedSIDmodel=0; cRSID_setSIDmodels(); break; }
     else { //in case a not known model is requested from outside:
      if (cRSID_getSIDmodel(1)==8580) cRSID.SelectedSIDmodel=6581;
      else if (cRSID_getSIDmodel(1)==6581) cRSID.SelectedSIDmodel=8580;
     }
     cRSID_setSIDmodel(1, cRSID_setSIDmodel(2, cRSID_setSIDmodel(3, cRSID_setSIDmodel(4, cRSID.SelectedSIDmodel) ) ) );
     break;
   }
   //if (PressedButton!=3) PressedButton=0;
  }
  PrevPressedButton=PressedButton;
  MouseButtonStates = SDL_GetMouseState(&MouseX,&MouseY);
  if ((MouseButtonStates & SDL_BUTTON_LMASK)==0) PressedButton=ScrollBarGrabbed=FilerScrollBarGrabbed=0;
  if ( ScrollBarGrabbed && (ScreenUpdateCounter==0 || !PrevScrollBarGrabbed) ) {
   Tmp = (MouseY-SCROLLBAR_Y) * cRSID.PlayListSize / SCROLLBAR_HEIGHT;
   if (Tmp<0) Tmp=0;
   if (Tmp >= cRSID.PlayListSize-PLAYLIST_ROWS) Tmp = cRSID.PlayListSize-PLAYLIST_ROWS-1;
   cRSID.PlayListDisplayPosition = Tmp; drawPlayList(0);
  }
  PrevScrollBarGrabbed = ScrollBarGrabbed;
  if ( FilerScrollBarGrabbed && (ScreenUpdateCounter==0 || !PrevFilerScrollBarGrabbed) ) {
   Tmp = (MouseY-FILER_SCROLLBAR_Y) * FileListSize / FILER_SCROLLBAR_HEIGHT;
   if (Tmp<0) Tmp=0;
   if (Tmp >= FileListSize-FILELIST_ROWS) Tmp = FileListSize-FILELIST_ROWS-1;
   FileListDisplayPosition = Tmp; drawFileList();
  }
  PrevFilerScrollBarGrabbed = FilerScrollBarGrabbed;

  if (cRSID.PlayListAdvance) startNextPlayListItem();
  else if (cRSID.PlaytimeExpired) { cRSID_nextSubtune(); FirstDraw=1; }


  //KeyStates = SDL_GetKeyState(NULL);
  if ( (KeyStates[SDLK_TAB]  //if ( (KeyStates[SDLK_TAB] && !KeyStates[SDLK_LALT]) || KeyStates[SDLK_BACKQUOTE] || KeyStates[SDLK_END] || KeyStates[SDLK_RIGHT] ) {
  #ifndef WINDOWS
   && !KeyStates[SDLK_LALT]  //in Linux we protect from Alt+TAB to fast-forward but in Windows the
  #endif                     //ALT-behaviour is buggy (gets stuck) so there we ignore ALT state for fast-fwd
                            ) || KeyStates[SDLK_BACKQUOTE] ) {
   cRSID.PlaybackSpeed=FFWD_SPEED; PressedButton=3;
  }
  else if (PressedButton==3) cRSID.PlaybackSpeed=FFWD_SPEED;
  else cRSID.PlaybackSpeed=1;


  if(ScreenUpdateCounter<SCREENUPDATE_PERIOD) ++ScreenUpdateCounter;  else { ScreenUpdateCounter=0; drawGUI(); }

  #if (USING_AUDIO_LIBRARY__ALSA)
   cRSID_syncGenSamples( INPUTSCAN_PERIOD );
  #else  //#elif (USING_AUDIO_LIBRARY__SDL)
   SDL_Delay( INPUTSCAN_PERIOD );
  #endif
 }
}

