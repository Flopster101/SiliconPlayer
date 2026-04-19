
enum WindowTheme {
 MARGIN_COLOR=0x202830, BACKGROUND_COLOR=MARGIN_COLOR, MARGIN_THICKNESS=4, FIELD_CHARCOUNT_MAX=32,
 COLOR_HELPTEXT=0xFFFFFF, HELP_GRADIENT_COLOR=0x105040, FONTHEIGHT = 11, FONTSIZE_HELPTEXT=10,
 FILEDIALOG_BACKGROUND_COLOR = 0x708090, FILEDIALOG_FILE_COLOR = 0x101010, FILEDIALOG_SCROLLBAR_COLOR = 0x505050,
 MENU_BACKGROUND_COLOR = 0x404040, PLAYLIST_BORDER_COLOR = 0x080808,
 FILELIST_ENTRIES_MAX = 1024, FILELIST_ENTRY_SIZE = 256
};


#include "widgets.c"


static short WinHeight = PLAYER_HEIGHT, WinWidth = PLAYER_WIDTH;
static char MenuMode = 0;

static      /*unsigned*/ char* PushButtonTexts[] =  { "\x1E\x1F", "\x1D"/*"1F"*/, "\x1F\x1F", "<", ">",  /* "ste" ,*/ "adv." , "SID" };
static const byte PushButtonSeparation[] =          {      0,       0,                  0,     1,   1 ,    /* 2   ,*/   2    ,   3   };
static word PushButtonX[] =    /*gets calculated*/  {      0,       0,                  0,     0,   0,     /* 0,*/      0    ,   0   };

static byte PressedButton=0, MenuButtonState=0, PlaybackButtonState=0;
static byte BackButtonState=0, OpenButtonState=0, AddButtonState=0, DelButtonState=0, SaveButtonState=0;
static byte QualityButtonState=0, ChannelsButtonState=0;
char FileList [FILELIST_ENTRIES_MAX] [FILELIST_ENTRY_SIZE];
unsigned int FileTypes [FILELIST_ENTRIES_MAX]; // 0:folder, 1:file
int FileListSize = 0, FileListDisplayPosition = 0, FileListCursorPosition = 0; //top of the playlist in GUI-display


enum ButtonLayout { PUSHBUTTON_AMOUNT = sizeof(PushButtonTexts) / sizeof(char*),
                    PUSHBUTTON_HEIGHT = 30, PUSHBUTTON_WIDTH=33/*29*//*28*/, BUTTONS_Y=PLAYER_HEIGHT/*WINHEIGHT*/-MARGIN_THICKNESS*2-PUSHBUTTON_HEIGHT,
                    PUSHBUTTON_SPACING=5, PUSHBUTTON_PADDING=(PUSHBUTTON_WIDTH+PUSHBUTTON_SPACING),
                    PAGEBUTTONS_GAP=2,
                    ROWHEIGHT_PLAYLIST=ELEMENT_HEIGHT, PUSHBUTTONS_WIDTH = ( PUSHBUTTON_AMOUNT * (PUSHBUTTON_PADDING) + 0 ),
                    ROWHEIGHT_FILELIST=ELEMENT_HEIGHT
                  };


//Fields (blocks) & Separators
//enum FieldLayout { };


unsigned char* valueToHexString (word value) {
 static unsigned char String[10];
 sprintf( (char*)String, "$%4.4X", value );
 return String;
}


 enum Layout {
 #if (BOTTOM_PLAYLIST)
  TOP_MARGIN = 0,
 #else
  TOP_MARGIN = 2,
 #endif
               MENUBUTTON_X = MARGIN_THICKNESS, MENUBUTTON_Y = MARGIN_THICKNESS + TOP_MARGIN,
                MENUBUTTON_WIDTH = 65, MENUBUTTON_HEIGHT = 18, MENUBUTTON_FONTSIZE = 10,
               PLAYBACK_BUTTON_X = (MENUBUTTON_X + MENUBUTTON_WIDTH + PAGEBUTTONS_GAP),
               QUALITY_BUTTON_WIDTH=40, QUALITY_LABEL_WIDTH = 28, QUALITY_BUTTON_Y = MARGIN_THICKNESS + TOP_MARGIN, //QUALITY_BUTTON_X = (MENUBUTTON_X+MENUBUTTON_WIDTH+PUSHBUTTON_SPACING*2),
                QUALITY_BUTTON_X = (WINWIDTH-MARGIN_THICKNESS-QUALITY_BUTTON_WIDTH-QUALITY_LABEL_WIDTH),
                QUALITY_LABEL_X = (QUALITY_BUTTON_X+QUALITY_BUTTON_WIDTH+1),
               CHANNELS_BUTTON_WIDTH = 50, CHANNELS_LABEL_WIDTH = 41, CHANNELS_BUTTON_Y = (QUALITY_BUTTON_Y),
                CHANNELS_BUTTON_X = (QUALITY_BUTTON_X - CHANNELS_BUTTON_WIDTH - CHANNELS_LABEL_WIDTH - 7), CHANNELS_LABEL_X = (CHANNELS_BUTTON_X+CHANNELS_BUTTON_WIDTH+1),
               FIELDTITLE_X=MARGIN_THICKNESS, FIELDTITLE_WIDTH=50, TEXTFIELD_PADDING=22,
               AUTHOR_Y=(TOP_MARGIN*2 + MARGIN_THICKNESS+MENUBUTTON_HEIGHT+MARGIN_THICKNESS*2), AUTHOR_OFFSET_X = 0, //(MENUBUTTON_WIDTH),
                TITLE_Y=(AUTHOR_Y+TEXTFIELD_PADDING), RELEASEINFO_Y=(TITLE_Y+TEXTFIELD_PADDING),
               TEXTFIELD_X=(MARGIN_THICKNESS+FIELDTITLE_WIDTH), TEXTFIELD_WIDTH=(WINWIDTH-MARGIN_THICKNESS-TEXTFIELD_X),
               VALUETITLE_WIDTH=32, VALUEFIELD_WIDTH=50, VALUEFIELD_PADDING=(VALUETITLE_WIDTH+VALUEFIELD_WIDTH-4), //+4),
               LOADINFO_X=FIELDTITLE_X, LOADINFO_Y=(RELEASEINFO_Y+TEXTFIELD_PADDING+MARGIN_THICKNESS), LOADINFOVAL_X = (LOADINFO_X+VALUETITLE_WIDTH-3),
               ENDINFO_X=(LOADINFO_X+VALUEFIELD_PADDING), ENDINFO_Y=LOADINFO_Y, ENDINFOVAL_X = (ENDINFO_X+VALUETITLE_WIDTH-3),
               SIZEINFO_X=(ENDINFO_X+VALUEFIELD_PADDING+14), SIZEINFO_Y=LOADINFO_Y, SIZEINFOVAL_X = (SIZEINFO_X+VALUETITLE_WIDTH-3),
                SIZEINFO_WIDTH=(WINWIDTH-MARGIN_THICKNESS-(SIZEINFO_X+VALUETITLE_WIDTH) + 3),
               INITINFO_X=FIELDTITLE_X, INITINFO_Y=(SIZEINFO_Y+TEXTFIELD_PADDING), INITINFOVAL_X = (INITINFO_X+VALUETITLE_WIDTH-3),
               PLAYINFO_X=(INITINFO_X+VALUEFIELD_PADDING), PLAYINFO_Y=INITINFO_Y, PLAYINFOVAL_X = (PLAYINFO_X+VALUETITLE_WIDTH-3),
               IRQINFO_X=(PLAYINFO_X+VALUEFIELD_PADDING+4+4), IRQINFO_Y=PLAYINFO_Y, IRQINFO_WIDTH=25,
               SUBTUNEINFO_X=(PLAYINFO_X+VALUEFIELD_PADDING+4+22), SUBTUNEINFO_Y=INITINFO_Y, SUBTUNEINFO_WIDTH=VALUEFIELD_WIDTH,
               PLAYTIMEINFO_X=(SUBTUNEINFO_X+SUBTUNEINFO_WIDTH+4), PLAYTIMEINFO_Y=INITINFO_Y, PLAYTIMEINFO_WIDTH=VALUEFIELD_WIDTH-6,
               STANDARDTITLE_WIDTH = 24, //25,
               STANDARDINFO_X=MARGIN_THICKNESS/*FIELDTITLE_X*/, SPEEDINFO_Y=(INITINFO_Y+TEXTFIELD_PADDING+MARGIN_THICKNESS),
                SPEEDTITLE_WIDTH=56, SPEEDINFOFIELD_WIDTH=42, SPEEDINFO_PADDING=(STANDARDTITLE_WIDTH/*SPEEDTITLE_WIDTH*/+SPEEDINFOFIELD_WIDTH+MARGIN_THICKNESS),
               TIMERINFO_X=(STANDARDINFO_X+SPEEDINFO_PADDING-2-1*1), TIMERINFO_Y=SPEEDINFO_Y, TIMERTITLE_WIDTH=37, //38, //50,
               FRAMESPEEDINFO_X=(TIMERINFO_X+TIMERTITLE_WIDTH+SPEEDINFOFIELD_WIDTH+MARGIN_THICKNESS-2*1), FRAMESPEEDINFO_Y=SPEEDINFO_Y, FRAMESPEEDTITLE_WIDTH=47, //50, //74,

               SID1INFO_X=FIELDTITLE_X, SID1INFO_Y=(SPEEDINFO_Y+TEXTFIELD_PADDING+MARGIN_THICKNESS), SIDINFOFIELD_WIDTH=107,
                SCOPE1_X=(SID1INFO_X+VALUETITLE_WIDTH+SIDINFOFIELD_WIDTH+MARGIN_THICKNESS)-1, SCOPE1_Y=SID1INFO_Y, SCOPE_WIDTH=14,
                SCOPE_HEIGHT=(TEXTFIELD_PADDING-MARGIN_THICKNESS), SCOPE_BGCOLOR=0x081810, SCOPE_FGCOLOR=0x60A080,
                SCOPE1_ACTIVE_X = (SCOPE1_X + SCOPE_BORDER), SCOPE1_ACTIVE_Y = (SCOPE1_Y + SCOPE_BORDER),
                SCOPE_ACTIVE_WIDTH = (SCOPE_WIDTH-SCOPE_BORDER*2+1), SCOPE_ACTIVE_HEIGHT = (SCOPE_HEIGHT-SCOPE_BORDER*2),
               SID2INFO_X=(WINWIDTH-(SIDINFOFIELD_WIDTH+FIELDTITLE_X+VALUETITLE_WIDTH)), SID2INFO_Y=SID1INFO_Y,
                SCOPE2_X=(SID2INFO_X-SCOPE_WIDTH-MARGIN_THICKNESS)+5, SCOPE2_Y=SCOPE1_Y,
                SCOPE2_ACTIVE_X = (SCOPE2_X + SCOPE_BORDER), SCOPE2_ACTIVE_Y = (SCOPE2_Y + SCOPE_BORDER),
               SID3INFO_X=FIELDTITLE_X, SID3INFO_Y=SID1INFO_Y+TEXTFIELD_PADDING,
                SCOPE3_X=SCOPE1_X, SCOPE3_Y=SID3INFO_Y,
                SCOPE3_ACTIVE_X = (SCOPE3_X + SCOPE_BORDER), SCOPE3_ACTIVE_Y = (SCOPE3_Y + SCOPE_BORDER),
               SID4INFO_X=SID2INFO_X, SID4INFO_Y=SID3INFO_Y,
                SCOPE4_X=SCOPE2_X, SCOPE4_Y=SCOPE3_Y,
                SCOPE4_ACTIVE_X = (SCOPE4_X + SCOPE_BORDER), SCOPE4_ACTIVE_Y = (SCOPE4_Y + SCOPE_BORDER),
               CONTROLS_X=MARGIN_THICKNESS, CONTROLS_Y=(SID3INFO_Y+TEXTFIELD_PADDING+MARGIN_THICKNESS)+5,

               POTMETER_X=(WINWIDTH-MARGIN_THICKNESS-POTMETER_RADIUS-14), POTMETER_Y=CONTROLS_Y+15,
                POTMETER_LEFT = (POTMETER_X-POTMETER_WIDTH/2), POTMETER_TOP = (POTMETER_Y-POTMETER_HEIGHT/2),
               PLAYTIME_WIDTH=42, PLAYTIME_X=WINWIDTH-PLAYTIME_WIDTH-MARGIN_THICKNESS, PLAYTIME_Y=SPEEDINFO_Y, //PLAYTIME_WIDTH=44, PLAYTIME_X=(POTMETER_X-POTMETER_WIDTH/2-MARGIN_THICKNESS-PLAYTIME_WIDTH-3), PLAYTIME_Y=(CONTROLS_Y+8),
 #if (BOTTOM_PLAYLIST)
               PLAYLIST_Y=PLAYER_HEIGHT, PLAYLIST_X=0,
 #else
               PLAYLIST_Y=0, PLAYLIST_X=PLAYER_WIDTH,
 #endif
               PLAYLIST_ELEMENTS_Y=PLAYLIST_Y+FIELDTITLE_HEIGHT,
               SCROLLBAR_Y=PLAYLIST_ELEMENTS_Y, SCROLLBAR_X=WINWIDTH-SCROLLBAR_THICKNESS-MARGIN_THICKNESS,
               PLAYLIST_WIDTH=WINWIDTH, PLAYLIST_HEIGHT=(PLAYLIST_ROWS*ROWHEIGHT_PLAYLIST+FIELDTITLE_HEIGHT+MARGIN_THICKNESS),
               SCROLLBAR_HEIGHT=PLAYLIST_HEIGHT-FIELDTITLE_HEIGHT,

               DIALOG_BUTTON_HEIGHT = 26, DIALOG_BUTTONS_Y = (PLAYER_HEIGHT-MARGIN_THICKNESS*2-DIALOG_BUTTON_HEIGHT),
               MENU_X=0, MENU_WIDTH=WINWIDTH, MENU_HEIGHT = (DIALOG_BUTTON_HEIGHT+MARGIN_THICKNESS*4), MENU_Y=(PLAYER_HEIGHT-MENU_HEIGHT),
               FILEDIALOG_X=0, FILEDIALOG_Y=(MARGIN_THICKNESS+MENUBUTTON_HEIGHT+MARGIN_THICKNESS+2), FILEDIALOG_WIDTH = (WINWIDTH),
               FILEDIALOG_HEIGHT = (PLAYER_HEIGHT-MENU_HEIGHT-MENUBUTTON_HEIGHT-MARGIN_THICKNESS*3),
               FILELIST_ELEMENTS_Y = (FILEDIALOG_Y + FIELDTITLE_HEIGHT), FILER_SCROLLBAR_Y=FILELIST_ELEMENTS_Y,
               FILER_SCROLLBAR_HEIGHT = (FILEDIALOG_HEIGHT-FIELDTITLE_HEIGHT), FILELIST_HEIGHT=(FILELIST_ROWS*ROWHEIGHT_FILELIST),
               OPEN_BUTTON_X = (MENU_X+MARGIN_THICKNESS), OPEN_BUTTON_Y = DIALOG_BUTTONS_Y, OPEN_BUTTON_WIDTH = 50, //45,
               ADD_BUTTON_X = (OPEN_BUTTON_X+OPEN_BUTTON_WIDTH+PUSHBUTTON_SPACING), ADD_BUTTON_Y = DIALOG_BUTTONS_Y, ADD_BUTTON_WIDTH=40, //35,
               DEL_BUTTON_X = (ADD_BUTTON_X+ADD_BUTTON_WIDTH+PUSHBUTTON_SPACING), DEL_BUTTON_Y = DIALOG_BUTTONS_Y, DEL_BUTTON_WIDTH=40, //35,
               SAVE_BUTTON_X = (DEL_BUTTON_X+DEL_BUTTON_WIDTH+PUSHBUTTON_SPACING*2), SAVE_BUTTON_Y = DIALOG_BUTTONS_Y, SAVE_BUTTON_WIDTH=100, //92,
               //QUALITY_BUTTON_X = (SAVE_BUTTON_X+SAVE_BUTTON_WIDTH+PUSHBUTTON_SPACING*2), QUALITY_BUTTON_Y = DIALOG_BUTTONS_Y, QUALITY_BUTTON_WIDTH=50,
                //QUALITY_LABEL_X = (QUALITY_BUTTON_X+QUALITY_BUTTON_WIDTH+1),
               BACK_BUTTON_WIDTH=45, BACK_BUTTON_X = (WINWIDTH-BACK_BUTTON_WIDTH-MARGIN_THICKNESS), BACK_BUTTON_Y = (OPEN_BUTTON_Y)
             };



static void fillRectangle (int x, int y, int w, int h, int color) {
 static SDL_Rect BackGroundRectangle;
 BackGroundRectangle.x=x, BackGroundRectangle.y=y; BackGroundRectangle.w=w; BackGroundRectangle.h=h;
 SDL_FillRect(Screen, &BackGroundRectangle, color); //SDL_FillRect(Screen, &Screen->clip_rect, MARGIN_COLOR);
}


static void checkDrawDialogButtons (char force_refresh) { //file-dialog / menu buttons
 static int BackPrevState = -1, OpenPrevState = -1, AddPrevState = -1, DelPrevState = -1, SavePrevState = -1;
 if (OpenPrevState != OpenButtonState || force_refresh) {
  OpenPrevState = OpenButtonState;
  fillRectangle( OPEN_BUTTON_X, OPEN_BUTTON_Y, OPEN_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT, MENU_BACKGROUND_COLOR );
  drawButton( OPEN_BUTTON_X, OPEN_BUTTON_Y, OPEN_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT, 0, "Open", OpenButtonState );
  SDL_UpdateRect( Screen, OPEN_BUTTON_X, OPEN_BUTTON_Y, OPEN_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT );
 }
 if (AddPrevState != AddButtonState || force_refresh) {
  AddPrevState = AddButtonState;
  fillRectangle( ADD_BUTTON_X, ADD_BUTTON_Y, ADD_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT, MENU_BACKGROUND_COLOR );
  drawButton( ADD_BUTTON_X, ADD_BUTTON_Y, ADD_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT, 0, "Add", AddButtonState );
  SDL_UpdateRect( Screen, ADD_BUTTON_X, ADD_BUTTON_Y, ADD_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT );
 }
 if (DelPrevState != DelButtonState || force_refresh) {
  DelPrevState = DelButtonState;
  fillRectangle( DEL_BUTTON_X, DEL_BUTTON_Y, DEL_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT, MENU_BACKGROUND_COLOR );
  drawButton( DEL_BUTTON_X, DEL_BUTTON_Y, DEL_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT, 0, "Del.", DelButtonState );
  SDL_UpdateRect( Screen, DEL_BUTTON_X, DEL_BUTTON_Y, DEL_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT );
 }
 if (SavePrevState != SaveButtonState || force_refresh) {
  SavePrevState = SaveButtonState;
  fillRectangle( SAVE_BUTTON_X, SAVE_BUTTON_Y, SAVE_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT, MENU_BACKGROUND_COLOR );
  drawButton( SAVE_BUTTON_X, SAVE_BUTTON_Y, SAVE_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT, 10, "Save Playlist", SaveButtonState );
  SDL_UpdateRect( Screen, SAVE_BUTTON_X, SAVE_BUTTON_Y, SAVE_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT );
 }
 if (BackPrevState != BackButtonState || force_refresh) {
  BackPrevState = BackButtonState;
  fillRectangle( BACK_BUTTON_X, BACK_BUTTON_Y, BACK_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT, MENU_BACKGROUND_COLOR );
  drawButton( BACK_BUTTON_X, BACK_BUTTON_Y, BACK_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT, 0, "Back", BackButtonState );
  SDL_UpdateRect( Screen, BACK_BUTTON_X, BACK_BUTTON_Y, BACK_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT );
 }
}


static void checkDrawMenuButtons (char force_refresh) {
 static int FilePrevState = -1, PlaybackPrevState, QualityPrevState = -1, ChannelPrevState = -1;
 MenuButtonState = MenuMode; PlaybackButtonState = !MenuMode;
 if (FilePrevState != MenuButtonState || force_refresh) {
  FilePrevState = MenuButtonState;
  fillRectangle( MENUBUTTON_X, MENUBUTTON_Y, MENUBUTTON_WIDTH, MENUBUTTON_HEIGHT, MARGIN_COLOR );
  drawPageButton( MENUBUTTON_X, MENUBUTTON_Y, MENUBUTTON_WIDTH, MENUBUTTON_HEIGHT, /*MENUBUTTON_FONTSIZE,*/ "File", MenuButtonState );
  SDL_UpdateRect( Screen, MENUBUTTON_X, MENUBUTTON_Y, MENUBUTTON_WIDTH, MENUBUTTON_HEIGHT );
 }
 if (PlaybackPrevState != PlaybackButtonState || force_refresh) {
  PlaybackPrevState = PlaybackButtonState;
  fillRectangle( PLAYBACK_BUTTON_X, MENUBUTTON_Y, MENUBUTTON_WIDTH, MENUBUTTON_HEIGHT, MARGIN_COLOR );
  drawPageButton( PLAYBACK_BUTTON_X, MENUBUTTON_Y, MENUBUTTON_WIDTH, MENUBUTTON_HEIGHT, /*MENUBUTTON_FONTSIZE,*/ "Playback", PlaybackButtonState );
  SDL_UpdateRect( Screen, PLAYBACK_BUTTON_X, MENUBUTTON_Y, MENUBUTTON_WIDTH, MENUBUTTON_HEIGHT );
 }
 if (QualityPrevState != QualityButtonState || force_refresh) {
  QualityPrevState = QualityButtonState;
  fillRectangle( QUALITY_BUTTON_X, QUALITY_BUTTON_Y, QUALITY_BUTTON_WIDTH+QUALITY_LABEL_WIDTH+1, MENUBUTTON_HEIGHT, BACKGROUND_COLOR );
  drawButton( QUALITY_BUTTON_X, QUALITY_BUTTON_Y, QUALITY_BUTTON_WIDTH, MENUBUTTON_HEIGHT, 8, "Quality", QualityButtonState );
  drawFieldTitle( QUALITY_LABEL_X, QUALITY_BUTTON_Y+1, QUALITY_LABEL_WIDTH,
                  cRSID.HighQualitySID ? (cRSID.HighQualityResampler?"Sinc":"High") : "Light" );
  SDL_UpdateRect( Screen, QUALITY_BUTTON_X, QUALITY_BUTTON_Y, QUALITY_BUTTON_WIDTH+QUALITY_LABEL_WIDTH+1, MENUBUTTON_HEIGHT );
 }
 if (ChannelPrevState != ChannelsButtonState || force_refresh) {
  ChannelPrevState = ChannelsButtonState;
  fillRectangle( CHANNELS_BUTTON_X, CHANNELS_BUTTON_Y, CHANNELS_BUTTON_WIDTH+CHANNELS_LABEL_WIDTH+1, MENUBUTTON_HEIGHT, BACKGROUND_COLOR );
  drawButton( CHANNELS_BUTTON_X, CHANNELS_BUTTON_Y, CHANNELS_BUTTON_WIDTH, MENUBUTTON_HEIGHT, 8, "Channels", ChannelsButtonState );
  drawFieldTitle( CHANNELS_LABEL_X, CHANNELS_BUTTON_Y+1, CHANNELS_LABEL_WIDTH,
                  cRSID.Stereo>=CRSID_CHANNELMODE_STEREO ? (cRSID.Stereo==CRSID_CHANNELMODE_STEREO?"Wide":"Stereo") : "Mono" );
  SDL_UpdateRect( Screen, CHANNELS_BUTTON_X, CHANNELS_BUTTON_Y, CHANNELS_BUTTON_WIDTH+CHANNELS_LABEL_WIDTH+1, MENUBUTTON_HEIGHT );
 }
}

static void checkDrawFrameSpeed (char force_refresh) {
 static unsigned char String[32];
 static int PrevFrameCycles = -1;
 if (PrevFrameCycles != cRSID.FrameCycles || force_refresh) {
  PrevFrameCycles = cRSID.FrameCycles;
  if (!cRSID.RealSIDmode) sprintf( (char*) String, "%.1fx", (cRSID.VideoStandard<=1? 19656.0:17095.0) / cRSID.FrameCycles ); //, C64->FrameCycles );
  drawCenteredTextField( FRAMESPEEDINFO_X+FRAMESPEEDTITLE_WIDTH, FRAMESPEEDINFO_Y, SPEEDINFOFIELD_WIDTH, (!cRSID.RealSIDmode)? (char*)String:"?" );
  SDL_UpdateRect( Screen, FRAMESPEEDINFO_X+FRAMESPEEDTITLE_WIDTH, FRAMESPEEDINFO_Y+MARGIN_THICKNESS-1, SPEEDINFOFIELD_WIDTH, FONTSIZE_TEXTFIELD+2 );
 }
}

static void checkDrawPlayTime (char force_refresh) {
 static unsigned char String[32];
 static int PrevPlayTime = -1;
 if (PrevPlayTime != cRSID.PlayTime || force_refresh) {
  PrevPlayTime = cRSID.PlayTime;
  sprintf ( (char*) String, "%2.2d:%2.2d", cRSID.PlayTime/60, cRSID.PlayTime%60 );
  drawTextField( PLAYTIME_X, PLAYTIME_Y, PLAYTIME_WIDTH, (char*) String );
  SDL_UpdateRect( Screen, PLAYTIME_X, PLAYTIME_Y+MARGIN_THICKNESS-1, PLAYTIME_WIDTH, FONTSIZE_TEXTFIELD+2 );
 }
}

static void checkDrawSIDinfo (char force_refresh) {
 static unsigned char String[32];
 static int PrevSID1model = -1, PrevSID2model = -1, PrevSID3model = -1, PrevSID4model = -1;
 if (PrevSID1model != cRSID_getSIDmodel(1) || force_refresh) {
  PrevSID1model = cRSID_getSIDmodel(1);
  sprintf( (char*) String, "$%4.4X,%d(%c)", cRSID_getSIDbase(1), cRSID_getSIDmodel(1), ChannelChar[cRSID_getSIDchannel(1)] );
  drawTextField( SID1INFO_X+VALUETITLE_WIDTH, SID1INFO_Y, SIDINFOFIELD_WIDTH, (char*) String ); //if(cRSID_C64.SIDchipCount==1) ;
  SDL_UpdateRect( Screen, SID1INFO_X+VALUETITLE_WIDTH, SID1INFO_Y+MARGIN_THICKNESS-1, SIDINFOFIELD_WIDTH, FONTSIZE_TEXTFIELD+2 );
 }
 if ( cRSID_getSIDbase(2) && (PrevSID2model != cRSID_getSIDmodel(2) || force_refresh) ) {
  PrevSID2model = cRSID_getSIDmodel(2);
  sprintf( (char*) String, "$%4.4X,%d(%c)", cRSID_getSIDbase(2), cRSID_getSIDmodel(2), ChannelChar[cRSID_getSIDchannel(2)] );
  drawTextField( SID2INFO_X+VALUETITLE_WIDTH, SID2INFO_Y, SIDINFOFIELD_WIDTH, (char*) String );
  SDL_UpdateRect( Screen, SID2INFO_X+VALUETITLE_WIDTH, SID2INFO_Y+MARGIN_THICKNESS-1, SIDINFOFIELD_WIDTH, FONTSIZE_TEXTFIELD+2 );
 }
 if ( cRSID_getSIDbase(3) && (PrevSID3model != cRSID_getSIDmodel(3) || force_refresh) ) {
  PrevSID3model = cRSID_getSIDmodel(3);
  sprintf( (char*) String, "$%4.4X,%d(%c)", cRSID_getSIDbase(3), cRSID_getSIDmodel(3), ChannelChar[cRSID_getSIDchannel(3)] );
  drawTextField( SID3INFO_X+VALUETITLE_WIDTH, SID3INFO_Y, SIDINFOFIELD_WIDTH, (char*) String );
  SDL_UpdateRect( Screen, SID3INFO_X+VALUETITLE_WIDTH, SID3INFO_Y+MARGIN_THICKNESS-1, SIDINFOFIELD_WIDTH, FONTSIZE_TEXTFIELD+2 );
 }
 if ( cRSID_getSIDbase(4) && (PrevSID4model != cRSID_getSIDmodel(4) || force_refresh) ) {
  PrevSID4model = cRSID_getSIDmodel(4);
  sprintf( (char*) String, "$%4.4X,%d(%c)", cRSID_getSIDbase(4), cRSID_getSIDmodel(4), ChannelChar[cRSID_getSIDchannel(4)] );
  drawTextField( SID4INFO_X+VALUETITLE_WIDTH, SID4INFO_Y, SIDINFOFIELD_WIDTH, (char*) String );
  SDL_UpdateRect( Screen, SID4INFO_X+VALUETITLE_WIDTH, SID4INFO_Y+MARGIN_THICKNESS-1, SIDINFOFIELD_WIDTH, FONTSIZE_TEXTFIELD+2 );
 }
}

static void checkDrawVUmeters (char force_refresh) {
 static int PrevLevelSID1 = -1;
 static int PrevLevelSID2 = -1;
 static int PrevLevelSID3 = -1;
 static int PrevLevelSID4 = -1;
 if (PrevLevelSID1 != cRSID_getSIDlevel(1) || force_refresh) {
  PrevLevelSID1 = cRSID_getSIDlevel(1);
  drawVUmeter( SCOPE1_X, SCOPE1_Y, SCOPE_WIDTH, SCOPE_HEIGHT, cRSID_getSIDlevel(1) >> 8 ); //, SCOPE_BGCOLOR, SCOPE_FGCOLOR );
  SDL_UpdateRect( Screen, SCOPE1_ACTIVE_X, SCOPE1_ACTIVE_Y, SCOPE_ACTIVE_WIDTH, SCOPE_ACTIVE_HEIGHT );
 }
 if ( cRSID_getSIDbase(2) && (PrevLevelSID2 != cRSID_getSIDlevel(2) || force_refresh) ) {
  PrevLevelSID2 = cRSID_getSIDlevel(2);
  drawVUmeter( SCOPE2_X, SCOPE2_Y, SCOPE_WIDTH, SCOPE_HEIGHT, cRSID_getSIDlevel(2) >> 8 ); //, SCOPE_BGCOLOR, SCOPE_FGCOLOR );
  SDL_UpdateRect( Screen, SCOPE2_ACTIVE_X, SCOPE2_ACTIVE_Y, SCOPE_ACTIVE_WIDTH, SCOPE_ACTIVE_HEIGHT );
 }
 if ( cRSID_getSIDbase(3) && (PrevLevelSID3 != cRSID_getSIDlevel(3) || force_refresh) ) {
  PrevLevelSID3 = cRSID_getSIDlevel(3);
  drawVUmeter( SCOPE3_X, SCOPE3_Y, SCOPE_WIDTH, SCOPE_HEIGHT, cRSID_getSIDlevel(3) >> 8 ); //, SCOPE_BGCOLOR, SCOPE_FGCOLOR );
  SDL_UpdateRect( Screen, SCOPE3_ACTIVE_X, SCOPE3_ACTIVE_Y, SCOPE_ACTIVE_WIDTH, SCOPE_ACTIVE_HEIGHT );
 }
 if ( cRSID_getSIDbase(4) && (PrevLevelSID4 != cRSID_getSIDlevel(4) || force_refresh) ) {
  PrevLevelSID4 = cRSID_getSIDlevel(4);
  drawVUmeter( SCOPE4_X, SCOPE4_Y, SCOPE_WIDTH, SCOPE_HEIGHT, cRSID_getSIDlevel(4) >> 8 ); //, SCOPE_BGCOLOR, SCOPE_FGCOLOR );
  SDL_UpdateRect( Screen, SCOPE4_ACTIVE_X, SCOPE4_ACTIVE_Y, SCOPE_ACTIVE_WIDTH, SCOPE_ACTIVE_HEIGHT );
 }
}

static void checkDrawButtons (char force_refresh) {
 static byte i;
 static int PrevButton = -1, PrevPaused = -1;
 if (PrevButton != PressedButton || PrevPaused != cRSID.Paused || force_refresh) {
  PrevButton = PressedButton; PrevPaused = cRSID.Paused;
  PushButtonTexts[1] = cRSID.Paused ? "\x1F" : "\x1D";
  //PushButtonTexts[5] = cRSID.Stereo ? "mo" : "ste";
  //PushButtonTexts[5/*7*/] = cRSID.AutoAdvance ? "rep": "adv";
  fillRectangle( CONTROLS_X, CONTROLS_Y, PUSHBUTTONS_WIDTH, PUSHBUTTON_HEIGHT, MARGIN_COLOR );
  for (i=0; i<PUSHBUTTON_AMOUNT; ++i) {
   PushButtonX[i] = CONTROLS_X + PUSHBUTTON_PADDING*i + PushButtonSeparation[i];
   drawButton( PushButtonX[i], CONTROLS_Y, PUSHBUTTON_WIDTH, PUSHBUTTON_HEIGHT, 0, PushButtonTexts[i], PressedButton==i+1 );
   setColor(0x303030); setFontSize(8);
   if (i==5) drawCenteredString( PushButtonX[i], CONTROLS_Y+(PUSHBUTTON_HEIGHT-8), PUSHBUTTON_WIDTH, 8,
                                 (unsigned char*) (cRSID.AutoAdvance? "on":"off") );
   if (i==6) drawCenteredString( PushButtonX[i], CONTROLS_Y+(PUSHBUTTON_HEIGHT-8), PUSHBUTTON_WIDTH, 8,
                                 (unsigned char*) (cRSID.SelectedSIDmodel==8580 ? "8580": (cRSID.SelectedSIDmodel==6581? "6581":"auto")) );
  }
  SDL_UpdateRect( Screen, CONTROLS_X, CONTROLS_Y, PUSHBUTTONS_WIDTH, PUSHBUTTON_HEIGHT );
 }
}

static void checkDrawPotMeter (char force_refresh) {
 static int PrevVolume = -1;
 if (PrevVolume != cRSID.MainVolume || force_refresh) {
  PrevVolume = cRSID.MainVolume;
  fillRectangle( POTMETER_LEFT, POTMETER_TOP, POTMETER_WIDTH, POTMETER_HEIGHT, MARGIN_COLOR );
  drawPotMeter( POTMETER_X, POTMETER_Y, cRSID.MainVolume, "Volume", VALUEMODE_PERCENTAGE );
  SDL_UpdateRect( Screen, POTMETER_LEFT, POTMETER_TOP, POTMETER_WIDTH, POTMETER_HEIGHT );
 }
}


static void drawFileList () {
 //fillRectangle( 0,0, WINWIDTH, FILEDIALOG_HEIGHT, FILEDIALOG_BACKGROUND_COLOR );
 drawSelector (FILEDIALOG_X, FILEDIALOG_Y, FILEDIALOG_WIDTH, FILEDIALOG_HEIGHT+MARGIN_THICKNESS, SCROLLSIDE_RIGHT,
            FILEDIALOG_BACKGROUND_COLOR, FILEDIALOG_FILE_COLOR, FILEDIALOG_SCROLLBAR_COLOR, "Select a .sid file to open...",
            (char*)FileList, NULL, NULL, FileTypes,
            FILELIST_ENTRY_SIZE, FileListDisplayPosition,
            FileListCursorPosition, FileListSize );
 SDL_UpdateRect( Screen, FILEDIALOG_X, FILEDIALOG_Y, FILEDIALOG_WIDTH, FILEDIALOG_HEIGHT+MARGIN_THICKNESS );
}


void drawOnce () {

 static byte i;
 static unsigned char String[32]; static word Tmp;


 fillRectangle( 0,0, WINWIDTH, AUTHOR_Y, MARGIN_COLOR );

 checkDrawMenuButtons(1);

 drawHorizontalSeparator ( MARGIN_THICKNESS, AUTHOR_Y-MARGIN_THICKNESS, WINWIDTH-MARGIN_THICKNESS);


 if (MenuMode) {
  fillRectangle( 0, AUTHOR_Y, MARGIN_THICKNESS-1, AUTHOR_Y+SID1INFO_Y, MARGIN_COLOR );
  fillRectangle( WINWIDTH-MARGIN_THICKNESS+1, AUTHOR_Y, MARGIN_THICKNESS-1, AUTHOR_Y+SID1INFO_Y, MARGIN_COLOR );
  drawFileList();
  fillRectangle( MENU_X, MENU_Y, MENU_WIDTH, MENU_HEIGHT, MENU_BACKGROUND_COLOR );
  checkDrawDialogButtons(1);
 } //end of menu/filedialog-mode


 else { //normal playback-control mode

  fillRectangle( 0, AUTHOR_Y, WINWIDTH, SID1INFO_Y, MARGIN_COLOR );

  drawFieldTitle( FIELDTITLE_X + AUTHOR_OFFSET_X, AUTHOR_Y, FIELDTITLE_WIDTH, "Author:" );
   drawTextField( TEXTFIELD_X + AUTHOR_OFFSET_X, AUTHOR_Y, TEXTFIELD_WIDTH - AUTHOR_OFFSET_X, cRSID.SIDheader->Author );

  drawFieldTitle( FIELDTITLE_X, TITLE_Y, FIELDTITLE_WIDTH, "Title:");
   drawTextField( TEXTFIELD_X, TITLE_Y, TEXTFIELD_WIDTH, cRSID.SIDheader->Title );

  drawFieldTitle( FIELDTITLE_X, RELEASEINFO_Y, FIELDTITLE_WIDTH, "Release:");
   drawTextField( TEXTFIELD_X, RELEASEINFO_Y, TEXTFIELD_WIDTH, cRSID.SIDheader->ReleaseInfo );


  drawHorizontalSeparator ( MARGIN_THICKNESS, LOADINFO_Y-MARGIN_THICKNESS-1, WINWIDTH-MARGIN_THICKNESS);


  drawFieldTitle( LOADINFO_X, LOADINFO_Y, VALUETITLE_WIDTH, "Load:");
   drawTextField( LOADINFOVAL_X, LOADINFO_Y, VALUEFIELD_WIDTH, (char*) valueToHexString(cRSID.LoadAddress) );

  drawFieldTitle( ENDINFO_X, ENDINFO_Y, VALUETITLE_WIDTH, "End:");
   drawTextField( ENDINFOVAL_X, ENDINFO_Y, VALUEFIELD_WIDTH, (char*) valueToHexString(cRSID.EndAddress) );

  drawFieldTitle( SIZEINFO_X, SIZEINFO_Y, VALUETITLE_WIDTH, "Size:");
   Tmp=cRSID.EndAddress-cRSID.LoadAddress; sprintf( (char*) String, "$%4.4X (%d)", Tmp, Tmp );
   drawTextField( SIZEINFOVAL_X, SIZEINFO_Y, SIZEINFO_WIDTH, (char*) String );

  drawFieldTitle( INITINFO_X, INITINFO_Y, VALUETITLE_WIDTH, "Init:");
   drawTextField( INITINFOVAL_X, INITINFO_Y, VALUEFIELD_WIDTH, (char*) valueToHexString(cRSID.InitAddress) );

  drawFieldTitle( PLAYINFO_X, PLAYINFO_Y, VALUETITLE_WIDTH, "Play:");
   drawTextField( PLAYINFOVAL_X, PLAYINFO_Y, VALUEFIELD_WIDTH+5, //+18,
                  (!cRSID.RealSIDmode)? (char*) valueToHexString(cRSID.PlayAddress) : "RealSID" );
    if (cRSID.SIDheader->PlayAddressH==0 && cRSID.SIDheader->PlayAddressL==0) {
     setFontSize(8); drawString(IRQINFO_X,IRQINFO_Y+4, (unsigned char*) "IRQ");
    }

  drawFieldTitle( SUBTUNEINFO_X, SUBTUNEINFO_Y, VALUETITLE_WIDTH, "Subtune:" );
   sprintf( (char*) String, "%d/%d", cRSID.SubTune, cRSID.SIDheader->SubtuneAmount );
   drawCenteredTextField( SUBTUNEINFO_X+VALUETITLE_WIDTH, SUBTUNEINFO_Y, SUBTUNEINFO_WIDTH, (char*) String );

  if (cRSID.SubtuneDurations[cRSID.SubTune]) {
   sprintf ( (char*) String, "%2.2d:%2.2d", cRSID.SubtuneDurations[cRSID.SubTune] / 60, cRSID.SubtuneDurations[cRSID.SubTune] % 60 );
   drawCenteredTextField( PLAYTIMEINFO_X+VALUETITLE_WIDTH, PLAYTIMEINFO_Y, PLAYTIMEINFO_WIDTH, (char*) String );
  }


  drawHorizontalSeparator ( MARGIN_THICKNESS, SPEEDINFO_Y-MARGIN_THICKNESS-1, WINWIDTH-MARGIN_THICKNESS);


  drawFieldTitle( STANDARDINFO_X, SPEEDINFO_Y, STANDARDTITLE_WIDTH/*SPEEDTITLE_WIDTH*/, "Std:");
   drawCenteredTextField( STANDARDINFO_X+STANDARDTITLE_WIDTH/*SPEEDTITLE_WIDTH*/, SPEEDINFO_Y, SPEEDINFOFIELD_WIDTH, cRSID.VideoStandard? "PAL":"NTSC" );

  drawFieldTitle( TIMERINFO_X, TIMERINFO_Y, TIMERTITLE_WIDTH, "Timer:");
   drawCenteredTextField( TIMERINFO_X+TIMERTITLE_WIDTH, TIMERINFO_Y, SPEEDINFOFIELD_WIDTH, cRSID.RealSIDmode? "?" : (cRSID.TimerSource? "CIA":"VIC") );

  drawFieldTitle( FRAMESPEEDINFO_X, FRAMESPEEDINFO_Y, FRAMESPEEDTITLE_WIDTH, "FrmSpd:"); //can change during playback (refresh regularly)
  checkDrawFrameSpeed(1);

  drawFieldTitle( PLAYTIME_X-PLAYTIME_WIDTH+5, PLAYTIME_Y, PLAYTIME_WIDTH, "Time:"); //drawFieldTitle( PLAYTIME_X-1, PLAYTIME_Y-TEXTFIELD_PADDING+6, PLAYTIME_WIDTH, "Time:");
  checkDrawPlayTime(1);


  drawHorizontalSeparator ( MARGIN_THICKNESS, SID1INFO_Y-MARGIN_THICKNESS-1, WINWIDTH-MARGIN_THICKNESS);

  fillRectangle( 0, SID1INFO_Y, WINWIDTH, PLAYER_HEIGHT/*WINHEIGHT*/-SID1INFO_Y, MARGIN_COLOR);

  drawFieldTitle( SID1INFO_X, SID1INFO_Y, VALUETITLE_WIDTH, "SID1:");
  if (cRSID_getSIDbase(2)) {
   drawFieldTitle( SID2INFO_X, SID2INFO_Y, VALUETITLE_WIDTH, "SID2:");
  }
  if (cRSID_getSIDbase(3)) {
   drawFieldTitle( SID3INFO_X, SID3INFO_Y, VALUETITLE_WIDTH, "SID3:");
  }
  if (cRSID_getSIDbase(4)) {
   drawFieldTitle( SID4INFO_X, SID4INFO_Y, VALUETITLE_WIDTH, "SID4:");
  }
  checkDrawSIDinfo(1);

  checkDrawVUmeters(1);


  drawHorizontalSeparator ( MARGIN_THICKNESS, SID3INFO_Y+TEXTFIELD_PADDING-1, WINWIDTH-MARGIN_THICKNESS);

  checkDrawButtons(1);

  checkDrawPotMeter(1); //drawPotMeter( POTMETER_X, POTMETER_Y, cRSID.MainVolume, "Volume", VALUEMODE_PERCENTAGE );

 } //end of normal playback-control mode
}


void drawPlayList (char force) {
 if (cRSID.BuiltInMusic==0) {
  if ( cRSID.PlayListSize < 2 && (!force || 
  #if (BOTTOM_PLAYLIST)
   WinHeight==PLAYER_HEIGHT
  #else
   WinWidth==PLAYER_WIDTH
  #endif
   ) ) return;
  fillRectangle( PLAYLIST_X, PLAYLIST_Y, WINWIDTH, MARGIN_THICKNESS, PLAYLIST_BORDER_COLOR );
  fillRectangle( PLAYLIST_X, PLAYLIST_Y, MARGIN_THICKNESS-1, PLAYLIST_HEIGHT, PLAYLIST_BORDER_COLOR );
  fillRectangle( PLAYLIST_X+WINWIDTH-MARGIN_THICKNESS+1, PLAYLIST_Y, MARGIN_THICKNESS-1, PLAYLIST_HEIGHT, PLAYLIST_BORDER_COLOR );
  fillRectangle( PLAYLIST_X, PLAYLIST_Y+PLAYLIST_HEIGHT-MARGIN_THICKNESS, WINWIDTH, MARGIN_THICKNESS, PLAYLIST_BORDER_COLOR );
  drawSelector (PLAYLIST_X, PLAYLIST_Y, WINWIDTH, PLAYLIST_HEIGHT, SCROLLSIDE_RIGHT, 0, 0, 0, "PlayList",
                (char*)cRSID.PlayList, cRSID.PlayTimeMinutes, cRSID.PlayTimeSeconds, cRSID.PlayListNumbering,
                CRSID_PLAYLIST_ENTRY_SIZE, cRSID.PlayListDisplayPosition,
                cRSID.PlayListPlayPosition, cRSID.PlayListSize );
 }
 else { //draw help
  setColor(HELP_GRADIENT_COLOR); setGradientColor(BACKGROUND_COLOR);
  drawGradientBox (PLAYLIST_X, PLAYLIST_Y, WINWIDTH, PLAYLIST_HEIGHT, GRADIENT_DISTANCE);
  setColor(COLOR_HELPTEXT); setFontSize(FONTSIZE_HELPTEXT);
  drawTextBox(PLAYLIST_X+2, PLAYLIST_Y
  #if (BOTTOM_PLAYLIST == 0)
   + 40
  #endif
   ,WINWIDTH,PLAYLIST_HEIGHT,HelpText);
 }
 SDL_UpdateRect( Screen, PLAYLIST_X/*0*/, PLAYLIST_Y, PLAYLIST_WIDTH/*WINWIDTH*/, PLAYLIST_HEIGHT );
}


void drawRepeatedly () {
 checkDrawMenuButtons(0);
 if (MenuMode) {
  checkDrawDialogButtons(0);
 }
 else { //normal playback-control mode
  checkDrawFrameSpeed(0);
  checkDrawPlayTime(0);
  checkDrawVUmeters(0);
  checkDrawSIDinfo(0);
  checkDrawButtons(0);
  checkDrawPotMeter(0);
 }
}