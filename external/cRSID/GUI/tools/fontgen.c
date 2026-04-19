#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <math.h>

#define DEBUG 0 //1
#define FILENAME_LENGTH_MAX 1024
#define FONT_BASESIZE 64
#define FONTWIDTH_MAX  128
#define FONTHEIGHT_MAX 128
#define FONTCODE_MIN 0x18 //20 //0x41
#define FONTCODE_MAX 0x7F //0x5A
#define FONTCODE_LIMIT 0x7F

int main () {

 FILE *InFile=NULL, *OutFile=NULL;
 const char FontPath[FILENAME_LENGTH_MAX]="GUI/graphics/charset/";
 const char AlphaDebugChars[8]={ '.' , ',' , ':' , ';', '+', '=', 'o', 'O' };
 char InFileName[FILENAME_LENGTH_MAX], FontName[FILENAME_LENGTH_MAX];
 char OutFileName[FILENAME_LENGTH_MAX];
 const char MagicID[16]="";
 unsigned char FontCode=0x30, DownScaleFactor=8, FontSize=8;
 int i,j,k,x,y, InData=0, BitmapWidth=0, BitmapHeight=0, AlphamapWidth=0, AlphamapHeight=0, AlphaPixel=0;
 char FontBitmapData[FONTHEIGHT_MAX][FONTWIDTH_MAX];
 unsigned char FontAlphaData[FONTHEIGHT_MAX][FONTWIDTH_MAX];
 unsigned char FontWidths[FONTCODE_LIMIT], FontHeights;
 char ScaleSteps[] = { /*3, 4,*/ 5, 6, /*7,*/ 8, /*9, 10*/ };

 for (k=0; k<sizeof(ScaleSteps); ++k) {

 DownScaleFactor = ScaleSteps[k];
 FontSize=FONT_BASESIZE/DownScaleFactor;
 sprintf(OutFileName,"GUI/graphics/fonts-size%2.2d.h",FontSize);
 OutFile=fopen(OutFileName,"wb");
 if (OutFile==NULL) { printf("Couldn't open %s for writing\n",OutFileName); return -1; }

 for (FontCode=FONTCODE_MIN; FontCode<=FONTCODE_MAX; ++FontCode) {

 sprintf(FontName,"%2.2X",FontCode); strcat( FontName, ".pbm" );
 strcpy( InFileName, FontPath ); strcat( InFileName, FontName );
 InFile=fopen(InFileName,"rb");
 if (InFile!=NULL) {

  //reading PBM-header
  fscanf( InFile, "%s\n%d %d\n", &MagicID, &BitmapWidth, &BitmapHeight );
  if(DEBUG)
   printf( "PBM Bitmap-file %s:  MagicID:%s, Size:%dx%d pixels (scaledown %dx)\n", InFileName, MagicID, BitmapWidth, BitmapHeight, DownScaleFactor );

  //getting 2-color bitmap-data
  x=0;y=0;
  while ( (InData=fgetc(InFile)) != EOF ) { if(DEBUG) printf("%2.2X ",InData);
   for (i=0; i<8; ++i,++x) FontBitmapData[y][x] = ( InData & (0x80>>i) ) != 0;
   if (x>=BitmapWidth) { x=0; ++y; }
  }
  if(DEBUG) printf("\n");
  if(DEBUG) {
   for(y=0;y<BitmapHeight;++y) {
    for(x=0;x<BitmapWidth;++x) printf("%c",FontBitmapData[y][x]?'O':'.');
    printf("\n");
   }
  }

  //downscaling font
  if(DEBUG) printf("\n");
  AlphamapWidth = ceil(BitmapWidth/(float)DownScaleFactor); AlphamapHeight = ceil(BitmapHeight/(float)DownScaleFactor);
  FontWidths[FontCode]=AlphamapWidth; FontHeights=AlphamapHeight;
  for (y=0; y<AlphamapHeight; ++y) {
   for (x=0; x<AlphamapWidth; ++x) {

    //nearest neighbour
    //printf( "%c", FontBitmapData[ (int)round(y*DownScaleFactor) ][ (int)round(x*DownScaleFactor) ] ? 'O':'.' );

    //interpolated/bilinear
    for ( i = (int)round(y*DownScaleFactor), AlphaPixel=0; i < (int)round((y+1)*DownScaleFactor); ++i ) {
     for ( j = (int)round(x*DownScaleFactor); j < (int)round((x+1)*DownScaleFactor); ++j ) {
      if (i<BitmapHeight && j<BitmapWidth) AlphaPixel += FontBitmapData[i][j];
     }
    }
    AlphaPixel = (AlphaPixel/pow(DownScaleFactor,2))*255.0; if(DEBUG) printf( "%c", AlphaDebugChars[AlphaPixel/32] ); //printf("%d", AlphaPixel);
    FontAlphaData[y][x] = AlphaPixel;

   }
   if(DEBUG) printf("\n");
  }

  //writing font to fonts.h datafile
  fprintf(OutFile, "const unsigned char Font%2.2Xs%d[] = { // \'", FontCode, FontSize);
  if (FontCode=='\"') fprintf(OutFile, "double-quote");
  else if (FontCode==0x7F) fprintf(OutFile, "square");
  else fprintf(OutFile, "%c", FontCode);
  fputs("\'\n",OutFile);
  for (y=0; y<AlphamapHeight; ++y) {
   fputs(" ",OutFile);
   for (x=0; x<AlphamapWidth; ++x) {
    fprintf(OutFile,"0x%2.2X",FontAlphaData[y][x]);
    if (y<AlphamapHeight-1 || x<AlphamapWidth-1) fputc(',',OutFile);
   }
   fputs("\n",OutFile);
  }
  fputs("};",OutFile);

 }
 else { printf("Couldn't open %s for reading\n",InFileName); FontWidths[FontCode]=0; continue; } //return -1; }
 if (DEBUG) printf("\n");

 fclose(InFile); fputs("\n",OutFile);

 } //FontCode loop

 fprintf(OutFile,"\nconst unsigned char* FontPointers%d[]={\n",FontSize);
 for (FontCode=FONTCODE_MIN; FontCode<=FONTCODE_MAX; ++FontCode) {
  if (FontWidths[FontCode]) fprintf(OutFile, "Font%2.2Xs%d,", FontCode, FontSize);
  else fprintf(OutFile, "    NULL,");
  if((FontCode%8)==7) fputs("\n",OutFile);
 }
 fputs(" NULL\n};\n",OutFile);

 fprintf(OutFile,"\nconst unsigned char FontWidths%d[]={\n",FontSize);
 for (FontCode=FONTCODE_MIN; FontCode<=FONTCODE_MAX; ++FontCode) {
  fprintf(OutFile, "%d,", FontWidths[FontCode]); if((FontCode%16)==15) fputs("\n",OutFile);
 }
 fputs(" 0\n};\n",OutFile);

 fprintf(OutFile,"\nconst unsigned char FontHeights%d=%d;\n",FontSize,FontHeights);


 fclose(OutFile);

 } //DownScaleFactor loop

 return 0;
}