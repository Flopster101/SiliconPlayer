//Graphic primitives

#include <SDL.h>

//#include "graphics/fonts-size06.h"
//#include "graphics/fonts-size07.h"
#include "graphics/fonts-size08.h"
#include "graphics/fonts-size10.h"
#include "graphics/fonts-size12.h"
//#include "graphics/fonts-size16.h"


#include "fixedpoint/fixedpoint.c" //#include <stdfix.h> //or any fixed-point arithmetic library to make alpha-pixelling faster on non-FPU CPUs
//#include <math.h>


#define PI 3.14159
#define RAD (PI/180.0)
#define SQRT05 0.7071
#define ANGLERESOMUL ( (360*SQRT05) / (2.0*PI) ) //to draw pixels of circle at 1-pixel distance
#define GRADIENT_DISTANCE_BALANCE 0.9 //0.7 //0.9 //max. 0.9, how far gradient-color blends into the normal color
#define GRADIENT_DISTANCE_BALANCE_CIRCLE 1.0


enum FontProperties { CHARCODE_MIN=0x18, FONT_SPACING=1, BUTTONMARGIN=1 };
enum GradientTypes { GRADIENT_HORIZONTAL, GRADIENT_VERTICAL, GRADIENT_DIAGONAL,
                     GRADIENT_CENTER_HORIZONTAL, GRADIENT_CENTER_VERTICAL,
                     GRADIENT_CENTER, GRADIENT_DISTANCE };
//enum ArrowDirections { ARROW_UP=1, ARROW_DOWN=-1 };


static SDL_Surface *Screen;
static byte/*Uint32*/ *Pixels;
static byte DrawRed=0, DrawGreen=0, DrawBlue=0, FontSize=8;
static byte DrawRedGradient=0, DrawGreenGradient=0, DrawBlueGradient=0;
static fixed DrawAlpha = FIXED(1); //float DrawAlpha=1.0;
static byte **FontSetPointer=(byte**)FontPointers8, *FontWidthPointer=(byte*)FontWidths8, FontHeight=FONTHEIGHT; //11; //FontHeights8;


static INLINE void setColor (FASTVAR dword color) {
 DrawBlue=color&0xFF; color>>=8; DrawGreen=color&0xFF; DrawRed=color>>8; DrawAlpha=FIXED(1);
}

static INLINE void setRGBcolor (FASTVAR byte R, FASTVAR byte G, FASTVAR byte B) {
 DrawRed=R; DrawGreen=G; DrawBlue=B; DrawAlpha=FIXED(1);
}

static INLINE void setGradientColor (FASTVAR dword color) {
 DrawBlueGradient=color&0xFF; color>>=8; DrawGreenGradient=color&0xFF; DrawRedGradient=color>>8;
}

static INLINE void setGradientRGBcolor (FASTVAR byte R, FASTVAR byte G, FASTVAR byte B) {
 DrawRedGradient=R; DrawGreenGradient=G; DrawBlueGradient=B;
}

static INLINE void setColorAlpha (FASTVAR dword color) {
 DrawBlue=color&0xFF; color>>=8; DrawGreen=color&0xFF; color>>=8; DrawRed=color&0xFF; DrawAlpha=byte2frac(color>>8); ///255.0;
}

static INLINE void setRGBcolorAlpha (FASTVAR byte R, FASTVAR byte G, FASTVAR byte B, FASTVAR fixed A) {
 DrawRed=R; DrawGreen=G; DrawBlue=B; DrawAlpha=A;
}

static INLINE byte getRed (FASTVAR dword color) { return (color>>16) & 0xFF; }
static INLINE byte getGreen (FASTVAR dword color) { return (color>>8) & 0xFF; }
static INLINE byte getBlue (FASTVAR dword color) { return color & 0xFF; }

static INLINE dword createRGBcolor (FASTVAR byte red, FASTVAR byte green, FASTVAR byte blue)
                                   { return (red<<16) | (green<<8) | blue; }

static INLINE dword blendColors (FASTVAR dword color1, FASTVAR dword color2, FASTVAR byte ratio) { //ratio: 0..255
 //byte Alpha = (color>>24); color1 &= 0x00FFFFFF;
 FASTVAR word Mul2 = ratio*0x100 / 0xFF, Mul1 = 0x100 - ratio;
 return createRGBcolor( ((getRed(color1)*Mul1)>>8)   + ((getRed(color2)*Mul2)>>8),
                        ((getGreen(color1)*Mul1)>>8) + ((getGreen(color2)*Mul2)>>8),
                        ((getBlue(color1)*Mul1)>>8)  + ((getBlue(color2)*Mul2)>>8) );
}

static INLINE void setFontSize (FASTVAR byte size) {
 switch (size) {
  //case 6: FontSetPointer=(byte**)FontPointers6; FontWidthPointer=(byte*)FontWidths6; FontHeight=FontHeights6; break;
  //case 7: FontSetPointer=(byte**)FontPointers7; FontWidthPointer=(byte*)FontWidths7; FontHeight=FontHeights7; break;
  case 8: FontSetPointer=(byte**)FontPointers8; FontWidthPointer=(byte*)FontWidths8; FontHeight=FontHeights8; break;
  case 10: FontSetPointer=(byte**)FontPointers10; FontWidthPointer=(byte*)FontWidths10; FontHeight=FontHeights10; break;
  case 12: FontSetPointer=(byte**)FontPointers12; FontWidthPointer=(byte*)FontWidths12; FontHeight=FontHeights12; break;
  //case 16: FontSetPointer=(byte**)FontPointers16; FontWidthPointer=(byte*)FontWidths16; FontHeight=FontHeights16; break;
 }
}


static INLINE void drawRawPixel (FASTVAR word x, FASTVAR word y) {
 FASTVAR int PixIndex;
 PixIndex = y*Screen->pitch + (x << LOG2_BYTES_PER_PIXEL);  // * BYTES_PER_PIXEL; //if(PixIndex>PixelArraySize) return;
 Pixels[PixIndex+0] = DrawBlue;
 Pixels[PixIndex+1] = DrawGreen;
 Pixels[PixIndex+2] = DrawRed;
}

static INLINE void drawGradientPixel (FASTVAR word x, FASTVAR word y, FASTVAR fixed grad) { //with gradient-support
 FASTVAR int PixIndex; FASTVAR fixed /*Grad=float2fixed(grad),*/ Rgrad = FIXED(1) - grad; //Grad;
 PixIndex = y*Screen->pitch + (x << LOG2_BYTES_PER_PIXEL);  // * BYTES_PER_PIXEL; //if(PixIndex>PixelArraySize) return;
 Pixels[PixIndex+0] = fmul2byte( DrawBlue, Rgrad )  + fmul2byte( DrawBlueGradient, grad );
 Pixels[PixIndex+1] = fmul2byte( DrawGreen, Rgrad ) + fmul2byte( DrawGreenGradient, grad );
 Pixels[PixIndex+2] = fmul2byte( DrawRed, Rgrad )   + fmul2byte( DrawRedGradient, grad );
}

static INLINE void drawPixel (FASTVAR word x, FASTVAR word y) { //with alpha-support
 FASTVAR int PixIndex;
 PixIndex = y*Screen->pitch + (x << LOG2_BYTES_PER_PIXEL);  //x*BYTES_PER_PIXEL; //if(PixIndex>PixelArraySize) return;
 Pixels[PixIndex+0] += fmul2byte( DrawBlue  - Pixels[PixIndex+0], DrawAlpha );
 Pixels[PixIndex+1] += fmul2byte( DrawGreen - Pixels[PixIndex+1], DrawAlpha );
 Pixels[PixIndex+2] += fmul2byte( DrawRed   - Pixels[PixIndex+2], DrawAlpha );
}

static INLINE void drawAlphaPixel (FASTVAR word x, FASTVAR word y, FASTVAR fixed alpha) { //, unsigned char alpha) {
 FASTVAR int PixIndex; FASTVAR fixed Alpha = fixmul( alpha, DrawAlpha ); //float2fixed( alpha*DrawAlpha );
 PixIndex = y*Screen->pitch + (x << LOG2_BYTES_PER_PIXEL); //x*BYTES_PER_PIXEL;
 Pixels[PixIndex+0] += fmul2byte( DrawBlue  - Pixels[PixIndex+0], Alpha );
 Pixels[PixIndex+1] += fmul2byte( DrawGreen - Pixels[PixIndex+1], Alpha );
 Pixels[PixIndex+2] += fmul2byte( DrawRed   - Pixels[PixIndex+2], Alpha );
}

static INLINE void drawGradientAlphaPixel (FASTVAR word x, FASTVAR word y, FASTVAR fixed alpha) { //, unsigned char alpha) {
 FASTVAR int PixIndex; FASTVAR fixed Alpha = fixmul( /*float2fixed*/(alpha), DrawAlpha ); //float2fixed( alpha*DrawAlpha );
 PixIndex = y*Screen->pitch + (x << LOG2_BYTES_PER_PIXEL); //x*BYTES_PER_PIXEL;
 Pixels[PixIndex+0] += fmul2byte( DrawBlueGradient  - Pixels[PixIndex+0], Alpha );
 Pixels[PixIndex+1] += fmul2byte( DrawGreenGradient - Pixels[PixIndex+1], Alpha );
 Pixels[PixIndex+2] += fmul2byte( DrawRedGradient   - Pixels[PixIndex+2], Alpha );
}

static INLINE void drawHorizontalPixel (FASTVAR fixed /*float*/ x, FASTVAR word y) { //anti-aliased horizontally
 FASTVAR int PixIndex; /*FASTVAR word intx;*/ FASTVAR fixed fracx, rfracx;
 /*intx = fixed2word(x)*/ /*(word)x*/; fracx = fixmul( frac(x) /*fracFloat(x)*/ /*float2fixed( x-intx )*/, DrawAlpha ); // float2fixed( (x-intx)*DrawAlpha );
 rfracx = /*float2fixed*/(DrawAlpha) - fracx;
 PixIndex = y*Screen->pitch + (fixed2word(x) /*intx*/ << LOG2_BYTES_PER_PIXEL); // intx*BYTES_PER_PIXEL;
 Pixels[PixIndex+0] += fmul2byte( DrawBlue  - Pixels[PixIndex+0], rfracx );
 Pixels[PixIndex+1] += fmul2byte( DrawGreen - Pixels[PixIndex+1], rfracx );
 Pixels[PixIndex+2] += fmul2byte( DrawRed   - Pixels[PixIndex+2], rfracx );
 PixIndex += BYTES_PER_PIXEL;
 Pixels[PixIndex+0] += fmul2byte( DrawBlue  - Pixels[PixIndex+0], fracx );
 Pixels[PixIndex+1] += fmul2byte( DrawGreen - Pixels[PixIndex+1], fracx );
 Pixels[PixIndex+2] += fmul2byte( DrawRed   - Pixels[PixIndex+2], fracx );
}

static INLINE void drawVerticalPixel (FASTVAR word x, FASTVAR fixed /*float*/ y) { //anti-aliased vertically
 FASTVAR int PixIndex; /*FASTVAR word inty;*/ FASTVAR fixed fracy, rfracy;
 /*inty = fixed2word(y)*/ /*(word)y*/; fracy = fixmul( frac(y) /*fracFloat(y)*/ /*float2fixed( y-inty )*/, DrawAlpha ); //float2fixed( (y-inty)*DrawAlpha );
 rfracy = /*float2fixed*/(DrawAlpha) - fracy;
 PixIndex = fixed2word(y) /*inty*/ * Screen->pitch + (x << LOG2_BYTES_PER_PIXEL); //x*BYTES_PER_PIXEL;
 Pixels[PixIndex+0] += fmul2byte( DrawBlue  - Pixels[PixIndex+0], rfracy );
 Pixels[PixIndex+1] += fmul2byte( DrawGreen - Pixels[PixIndex+1], rfracy );
 Pixels[PixIndex+2] += fmul2byte( DrawRed   - Pixels[PixIndex+2], rfracy );
 PixIndex += Screen->pitch;
 Pixels[PixIndex+0] += fmul2byte( DrawBlue  - Pixels[PixIndex+0], fracy );
 Pixels[PixIndex+1] += fmul2byte( DrawGreen - Pixels[PixIndex+1], fracy );
 Pixels[PixIndex+2] += fmul2byte( DrawRed   - Pixels[PixIndex+2], fracy );
}

static INLINE void drawAntiAliasedPixel (FASTVAR fixed /*float*/ x, FASTVAR fixed /*float*/ y) {
 FASTVAR int PixIndex; /*FASTVAR word intx,inty;*/ FASTVAR fixed fracx,rfracx,fracy,rfracy; //,portion;
 FASTVAR fixed Portion; //, FixedPointAlpha = /*float2fixed*/( DrawAlpha );
 //intx = (word)x; inty = (word)y;
 fracx = fixmul( frac(x) /*fracFloat(x)*/ /*float2fixed( x - intx )*/, DrawAlpha ); rfracx = DrawAlpha - fracx; //FixedPointAlpha - fracx;
 fracy = fixmul( frac(y) /*fracFloat(y)*/ /*float2fixed( y - inty )*/, DrawAlpha ); rfracy = DrawAlpha - fracy; //FixedPointAlpha - fracy;
 PixIndex = fixed2word(y) /*inty*/ * Screen->pitch + ( fixed2word(x) /*intx*/ << LOG2_BYTES_PER_PIXEL );
 Portion = fixmul( rfracx, rfracy ); //portion = rfracx * rfracy;
 Pixels[PixIndex+0] += fmul2byte( DrawBlue  - Pixels[PixIndex+0], Portion );
 Pixels[PixIndex+1] += fmul2byte( DrawGreen - Pixels[PixIndex+1], Portion );
 Pixels[PixIndex+2] += fmul2byte( DrawRed   - Pixels[PixIndex+2], Portion );
 PixIndex += BYTES_PER_PIXEL; Portion = fixmul( fracx, rfracy ); //portion = fracx * rfracy;
 Pixels[PixIndex+0] += fmul2byte( DrawBlue  - Pixels[PixIndex+0], Portion );
 Pixels[PixIndex+1] += fmul2byte( DrawGreen - Pixels[PixIndex+1], Portion );
 Pixels[PixIndex+2] += fmul2byte( DrawRed   - Pixels[PixIndex+2], Portion );
 PixIndex += Screen->pitch; Portion = fixmul( fracx, fracy ); //portion = fracx * fracy;
 Pixels[PixIndex+0] += fmul2byte( DrawBlue  - Pixels[PixIndex+0], Portion );
 Pixels[PixIndex+1] += fmul2byte( DrawGreen - Pixels[PixIndex+1], Portion );
 Pixels[PixIndex+2] += fmul2byte( DrawRed   - Pixels[PixIndex+2], Portion );
 PixIndex -= BYTES_PER_PIXEL; Portion = fixmul( rfracx, fracy ); //portion = rfracx * fracy;
 Pixels[PixIndex+0] += fmul2byte( DrawBlue  - Pixels[PixIndex+0], Portion );
 Pixels[PixIndex+1] += fmul2byte( DrawGreen - Pixels[PixIndex+1], Portion );
 Pixels[PixIndex+2] += fmul2byte( DrawRed   - Pixels[PixIndex+2], Portion );
}


static INLINE void drawHorizontalLine (FASTVAR word x1, FASTVAR word y, FASTVAR word x2) {
 for(;x1<=x2;++x1) drawPixel(x1,y);
}


static INLINE void drawVerticalLine (FASTVAR word x, FASTVAR word y1, FASTVAR word y2) {
 for(;y1<=y2;++y1) drawPixel(x,y1);
}


void drawLine (word x1, word y1, FASTVAR word x2, FASTVAR word y2) { //, color c, char wide) {
 FASTVAR word i; FASTVAR short step; FASTVAR fixed m, f; //, FixedPointM;
 step=+1; m = fixdiv( int2fixed(x2-x1), int2fixed(y2-y1) );
 if( CALMLY (y2==y1) ) { drawHorizontalLine( x1, y1, x2 ); return; } //if(y2==y1) m=1000000.0; else m = (x2-x1)/(float)(y2-y1);
 if ( FIXED(-1) < m && m < FIXED(1) ) {
  if ( TIGHTLY (y2 < y1) ) { step = -1; m = fixmul( m, FIXED(-1) ); }
  //FixedPointM = float2fixed(m);
  for (i=y1, f=word2fixed(x1); i!=y2; i+=step, f += m) { drawHorizontalPixel( f, i ); }
 } //,&c);} // if(wide){draw_pixel(f+1,i,&c);draw_pixel(f-1,i,&c);}} }
 else {
  if ( TIGHTLY (x2 < x1) ) { step = -1; m = fixdiv( FIXED(-1) , m ); } //FIXED(-1.0) doesn't work for some reason (fixdiv gives 0)
  else if ( CALMLY (x2==x1) ) { drawVerticalLine( x1, y1, y2 ); return; }
  else m = fixdiv( FIXED(1), m );
  //FixedPointM = float2fixed(m);
  for (i=x1, f=word2fixed(y1); i!=x2; i+=step, f += m) { drawVerticalPixel( i, f ); }
 } //,&c);} // if(wide){draw_pixel(i,f+1,&c);draw_pixel(i,f-1,&c);}} }
}


void drawAngledLine (FASTVAR word x1, FASTVAR word y1, FASTVAR word length, FASTVAR short angle) {
 FASTVAR fixed i, FixedPointX1, FixedPointY1; //, step; //static word x2, y2;
 //FASTVAR float AngleRad = angle*RAD;
 FASTVAR fixed Sin = fixsin(int2fixed(angle)/*AngleRad*/), Cos = fixcos(int2fixed(angle)/*AngleRad*/);
 //x2 = x1 + float2int( length * cos(angle*RAD) ); y2 = float2int( y1 - length * sin(angle*RAD) );
 length = word2fixed( length ); FixedPointX1 = word2fixed(x1); FixedPointY1 = word2fixed(y1);
 for (i=0; i<length; i+=FIXED(1)) {
  drawAntiAliasedPixel( fixadd(FixedPointX1, fixmul(i,Cos)), fixsub(FixedPointY1, fixmul(i,Sin)) );  //drawLine(x1,y1,x2,y2);
 }
}


static INLINE void drawRectangle (FASTVAR word x1, FASTVAR word y1, FASTVAR word x2, FASTVAR word y2) {
 //static word x,y;
 drawHorizontalLine(x1,y1,x2); drawHorizontalLine(x1,y2,x2); //for(x=x1;x<=x2;++x) { drawPixel(x,y1); drawPixel(x,y2); }
 drawVerticalLine(x1,y1,y2); drawVerticalLine(x2,y1,y2); //for(y=y1;y<=y2;++y) { drawPixel(x1,y); drawPixel(x2,y); }
}


static INLINE void drawBox (word x, word y, word w, word h) { //solid color (with alpha-support)
 FASTVAR word i,j,x2,y2;
 x2 = x + w; y2 = y + h;
 for (i=y; i < y2; ++i) for (j=x; j < x2; ++j) drawPixel(j,i);
}


static INLINE void drawVerticalBox (word x, word y, word w, word h) { //solid color (with alpha-support), faster for vertical boxes
 FASTVAR word i,j,x2,y2;
 x2 = x + w; y2 = y + h;
 for (i=x; i < x2; ++i) for (j=y; j < y2; ++j) drawPixel(i,j);
}


void drawGradientBox (FASTVAR word x, FASTVAR word y, FASTVAR word w, FASTVAR word h, FASTVAR byte gradtype) { //gradient (without alpha-support)
 FASTVAR word i,j,x2,y2,halfw,halfh; FASTVAR fixed distw,disth, Grad;
 switch (gradtype) {
  case GRADIENT_HORIZONTAL: x2=x+w; for(i=0; i<h; ++i) { Grad = fdivWord(i,h); for(j=x; j<x2; ++j) drawGradientPixel(j,y+i,Grad); } break;
  case GRADIENT_VERTICAL: y2=y+h; for(i=0; i<w; ++i) { Grad = fdivWord(i,w); for(j=y; j<y2; ++j) drawGradientPixel(x+i,j,Grad); } break;
  case GRADIENT_DIAGONAL: for(i=0; i<h; ++i) { for(j=0; j<w; ++j) {Grad = (fdivWord(i,h)+fdivWord(j,w))/2; drawGradientPixel(x+j,y+i,Grad);} } break;
  case GRADIENT_CENTER_HORIZONTAL: x2=x+w; halfh=h/2; for(i=0; i<h; ++i) { Grad = fdivByWord( fabsInt(halfh-i), halfh ); for(j=x; j<x2; ++j) drawGradientPixel(j,y+i,Grad); } break;
  case GRADIENT_CENTER_VERTICAL: y2=y+h; halfw=w/2; for(i=0; i<w; ++i) { Grad = fdivByWord( fabsInt(halfw-i), halfw ); for(j=y; j<y2; ++j) drawGradientPixel(x+i,j,Grad); } break;
  case GRADIENT_CENTER: halfh=h/2; halfw=w/2;
   for(i=0; i<h; ++i) {
    for(j=0; j<w; ++j) drawGradientPixel( x+j, y+i, ( fdivByWord( fabsInt(halfh-i), halfh ) + fdivByWord( fabsInt(halfw-j), halfw) ) / 2 );
   } break;
  case GRADIENT_DISTANCE: halfh=h/2; halfw=w/2; for(i=0; i<h; ++i) {
    for(j=0; j<w; ++j) {
     distw = fixdiv( fabsInt(halfw-j), word2fixed(halfw) ); disth = fixdiv( fabsInt(halfh-i), word2fixed(halfh) );
     drawGradientPixel( x+j, y+i, fixmul( fixsqrt( fixsqr(distw) + fixsqr(disth) ), FIXED(GRADIENT_DISTANCE_BALANCE) ) );
    }
   }  break;
   //default: break;
 }
}


void drawCircle (FASTVAR word x, FASTVAR word y, FASTVAR word r) { //circle
 FASTVAR word i,j,startx,starty,endx,endy,endr; FASTVAR short d; FASTVAR fixed s, FixedPointCoord;
 endr=r*SQRT05; starty=y-endr; endy=y+endr; startx=x-endr-1; endx=x+endr+1;
 for(i=starty; i<=endy; ++i) {
  d=i-y; s = fixsqrt(int2fixed(r*r-d*d)); FixedPointCoord = word2fixed(x);
  drawHorizontalPixel( FixedPointCoord - s, i ); drawHorizontalPixel( FixedPointCoord + s, i );
 }
 for(i=startx; i<=endx; ++i) {
  d=i-x; s = fixsqrt(int2fixed(r*r-d*d)); FixedPointCoord = word2fixed(y);
  drawVerticalPixel( i , FixedPointCoord - s ); drawVerticalPixel( i , FixedPointCoord + s );
 }
}


void drawArc (FASTVAR word x, FASTVAR word y, FASTVAR word r, FASTVAR word angle1, FASTVAR word angle2) {
 FASTVAR fixed a, step, Angle1, Angle2; //, AngleRad;
 FASTVAR fixed FixedPointX = word2fixed(x), FixedPointY = word2fixed(y), FixedPointR = word2fixed(r);
 step = fixdiv( FIXED(ANGLERESOMUL) , word2fixed(r) ); r = word2fixed( r ); x = word2fixed( x ); y = word2fixed( y ); //(X and Y as 'short int' might be too small here for >256 coordinates)
 Angle1 = word2fixed( angle1 ); Angle2 = word2fixed( angle2 );
 if(Angle2>Angle1) {
  for (a=Angle1; a<=Angle2; a+=step) {
   //AngleRad = a*RAD;
   drawAntiAliasedPixel( FixedPointX + fixmul(FixedPointR, fixcos(a) /*float2fixed(cos(AngleRad))*/) ,
                         FixedPointY - fixmul(FixedPointR, fixsin(a) /*float2fixed(sin(AngleRad))*/) );
  }
 }
 else {
  for (a=Angle2; a<=Angle1; a+=step) {
   //AngleRad = a*RAD;
   drawAntiAliasedPixel( FixedPointX + fixmul(FixedPointR, fixcos(a) /*float2fixed(cos(AngleRad))*/) ,
                         FixedPointY - fixmul(FixedPointR, fixsin(a) /*float2fixed(sin(AngleRad))*/) );
  }
 }
}


void drawDisc (FASTVAR word x, FASTVAR word y, FASTVAR word r) { //filled circle
 FASTVAR word i,j,startx,starty,endx,endy,endr,startAAy,endAAy,ints; FASTVAR short d; //FASTVAR float s;
 FASTVAR fixed s, fracs;
 starty = y-r; endy = y+r; endr = r*SQRT05; startAAy = y-endr; endAAy = y+endr;
 for (i=starty; i<=endy; ++i) {
  d = i-y; s = fixsqrt(int2fixed(r*r-d*d)); ints = fixed2word(s); fracs = frac(s); //(s-ints;
  startx = x-ints; endx = x+ints; for(j=startx; j<=endx; j++) drawPixel(j,i);
  if ( RARELY (startAAy<=i && i<=endAAy) ) { drawAlphaPixel(startx-1,i,fracs); drawAlphaPixel(endx+1,i,fracs); }
 }
 startx = x-endr-1; endx = x+endr+1;
 for (i=startx; i<endx; ++i) {
  d = i-x; s = fixsqrt(int2fixed(r*r-d*d)); ints = fixed2word(s); fracs = frac(s); //s-ints;
  drawAlphaPixel( i, y-ints-1, fracs ); drawAlphaPixel( i, y+ints+1, fracs );
 }
}


void drawGradientDisc (FASTVAR word x, FASTVAR word y, FASTVAR word r) { //filled circle with gradient towards the center
 FASTVAR short i,j,d; FASTVAR fixed s, distw,disth, fracs;
 FASTVAR word ints,endr,startAAy,endAAy,yi,startx,endx;
 endr = r*SQRT05; startAAy = y-endr; endAAy = y+endr;
 for (i=-r; i<=+r; ++i) {
  yi = y+i; s = fixsqrt(int2fixed(r*r-i*i)); ints = fixed2word(s); fracs = frac( s ); //float2fixed( s ) - word2fixed( ints );
  disth = fixdiv( fabsInt(i), word2fixed(r) ); //(float)abs(i)/r;
  for (j=-ints; j<=+ints; j++) {
   distw = fixdiv( fabsInt(j), word2fixed(r) ); //(float)abs(j)/r;
   drawGradientPixel( x+j,yi, fixmul( fixsqrt( fixsqr(distw) + fixsqr(disth) ), FIXED(GRADIENT_DISTANCE_BALANCE_CIRCLE) ) );
  }
  if ( RARELY (startAAy<=yi && yi<=endAAy) ) { drawGradientAlphaPixel(x-ints-1,yi,fracs); drawGradientAlphaPixel(x+ints+1,yi,fracs); }
 }
 startx = x-endr-1; endx = x+endr+1;
 for (i=startx; i<endx; ++i) {
  d = i-x; s = fixsqrt(int2fixed(r*r-d*d)); ints = fixed2word(s); fracs = frac( s );  //float2fixed( s ) - word2fixed( ints );  //s-ints;
  drawGradientAlphaPixel( i, y-ints-1, fracs); drawGradientAlphaPixel( i, y+ints+1, fracs );
 }
}


void drawQuarterDisc (FASTVAR word x, FASTVAR word y, FASTVAR word r, FASTVAR byte quarter) {
 FASTVAR word i,j,startx,starty,endx,endy,endr,startAAy,endAAy,ints; FASTVAR short d; FASTVAR fixed s, fracs;
 endr = r*SQRT05;
 if (quarter==0) {
  startAAy = y-endr;
  for (i=y-r; i<=y; ++i) {
   d = i-y; s = fixsqrt(int2fixed(r*r-d*d)); ints = fixed2word(s); fracs = frac(s); endx = x+ints;
   for (j=x; j<=endx; ++j) drawPixel( j, i );
   if ( RARELY (startAAy<=i) ) { drawAlphaPixel( endx+1, i, fracs ); }
  }
  endx = x+endr+1;
  for (i=x; i<=endx; ++i) {
   d = i-x; s = fixsqrt(int2fixed(r*r-d*d)); ints=fixed2word(s); fracs = frac(s);
   drawAlphaPixel(i,y-ints-1,fracs);
 }}
 else if (quarter==1) {
  startAAy = y-endr;
  for (i=y-r; i<=y; ++i) {
   d = i-y; s = fixsqrt(int2fixed(r*r-d*d)); ints = fixed2word(s); fracs = frac(s) ; endx = x-ints;
   for (j=x; j>=endx; --j) drawPixel( j, i );
   if ( RARELY (startAAy<=i) ) { drawAlphaPixel( endx-1, i, fracs ); }
  }
  endx = x-endr-1;
  for (i=x; i>=endx; --i) {
   d = i-x; s = fixsqrt(int2fixed(r*r-d*d)); ints = fixed2word(s); fracs = frac(s);
   drawAlphaPixel(i,y-ints-1,fracs);
 }}
 else if (quarter==2) {
  endy = y+r; endAAy = y+endr;
  for (i=y; i<=endy; ++i) {
   d = i-y; s=fixsqrt(int2fixed(r*r-d*d)); ints = fixed2word(s); fracs = frac(s); endx = x-ints;
   for(j=x; j>=endx; --j) drawPixel( j, i );
   if ( RARELY (i<=endAAy) ) { drawAlphaPixel( endx-1, i, fracs ); }
  }
  endx = x-endr-1;
  for (i=x; i>=endx; --i) {
   d = i-x; s = fixsqrt(int2fixed(r*r-d*d)); ints = fixed2word(s); fracs=frac(s);
   drawAlphaPixel( i, y+ints+1, fracs );
 }}
 else {
  endy = y+r; endAAy = y+endr;
  for (i=y; i<=endy; ++i) {
   d = i-y; s = fixsqrt(int2fixed(r*r-d*d)); ints = fixed2word(s); fracs = frac(s); endx = x+ints;
   for (j=x; j<=endx; ++j) drawPixel( j, i );
   if ( RARELY (i<=endAAy) ) { drawAlphaPixel( endx+1, i, fracs ); }
  }
  endx = x+endr+1;
  for(i=x; i<=endx; ++i) {
   d = i-x; s = fixsqrt(int2fixed(r*r-d*d)); ints = fixed2word(s); fracs = frac(s);
   drawAlphaPixel( i, y+ints+1, fracs );
 }}
}


void drawFilledTriangle (word x1, word y1, word x2, word y2, word x3, word y3) {
 FASTVAR word x,y, tmp, x4, y4, intxs, intxe; FASTVAR fixed xs, xe, slope1,slope2;
 if (y1>y2) {tmp=y2;y2=y1;y1=tmp; tmp=x2;x2=x1;x1=tmp;} if (y2>y3) {tmp=y3;y3=y2;y2=tmp; tmp=x3;x3=x2;x2=tmp;}
 if (y1>y2) {tmp=y2;y2=y1;y1=tmp; tmp=x2;x2=x1;x1=tmp;} //now y coordinates are ordered: y1 <= y2 <= y3
 x4 = ( x1 + ((float)(y2-y1)/(y3-y1)) * (x3-x1) );  y4 = y2;
 if (y1 != y2) { //bottom-is-flat sub-triangle of the main triangle
  slope1 = float2fixed( (float)(x2-x1)/(y2-y1) ); slope2 = float2fixed( (float)(x4-x1)/(y4-y1) );
  xs = xe = word2fixed(x1);
  if ( fixed2int(slope2) >= fixed2int(slope1) ) { //((int)xe>=(int)xs) {
   for (y=y1; y<=y4; y++) {
    intxs = fixed2word(xs); intxe = fixed2word(xe);
    for (x = intxs; x <= intxe; x++) drawPixel( x, y );
    drawAlphaPixel( intxs-1, y, FIXED(1)-frac(xs) /*1.0-(xs-((word)xs))*/ ); drawAlphaPixel( intxe+1, y, frac(xe) /*xe-((word)xe)*/ );
    xs += slope1; xe += slope2;
  }}
  else {
   for (y=y1; y<=y4; y++) {
    intxs = fixed2word(xs); intxe = fixed2word(xe);
    for (x=intxs; x >= intxe; x--) drawPixel( x, y );
    drawAlphaPixel( intxe-1, y, FIXED(1)-frac(xe) /*1.0-(xe-((word)xe))*/ ); drawAlphaPixel( intxs+1, y, frac(xs) /*xs-((word)xs)*/ );
    xs += slope1; xe += slope2;
 }}}
 if (y2 != y3) { //top-is-flat sub-triangle of the main triangle
  slope1 = float2fixed( (float)(x3-x2)/(y3-y2) ); slope2 = float2fixed( (float)(x3-x4)/(float)(y3-y4) );
  xs = xe = word2fixed(x3);
  if ( fixed2int(slope2) < fixed2int(slope1) ) { //((int)xe>=(int)xs) {
   for (y=y3; y>y4; y--) {
    intxs = fixed2word(xs); intxe = fixed2word(xe);
    for (x=intxs; x <= intxe; x++) drawPixel( x, y );
    drawAlphaPixel( intxs-1, y, FIXED(1)-frac(xs) /*1.0-(xs-((word)xs))*/ ); drawAlphaPixel( intxe+1, y, frac(xe) /*xe-((word)xe)*/ );
    xs -= slope1; xe -= slope2;
  }}
  else {
   for (y=y3; y>y4; y--) {
    intxs = fixed2word(xs); intxe = fixed2word(xe);
    for (x=intxs; x >= intxe; x--) drawPixel( x, y );
    drawAlphaPixel( intxe-1, y, FIXED(1)-frac(xe) /*1.0-(xe-((word)xe))*/ ); drawAlphaPixel( intxs+1, y, frac(xs) /*xs-((word)xs)*/ );
    xs -= slope1; xe -= slope2;
 }}}
}


void drawRoundedRectangle (FASTVAR word x, FASTVAR word y, word w, word h, FASTVAR word r) {
 FASTVAR word x1,x2,y1,y2,x3,y3;
 x1=x+r; x2=x+w; y1=y+r; y2=y+h; x3=x2-r; y3=y2-r;
 drawHorizontalLine(x1,y,x3); drawHorizontalLine(x1,y2,x3);
 drawVerticalLine(x,y1,y3); drawVerticalLine(x2,y1,y3);
 drawArc(x1,y1,r,90,180); drawArc(x3,y1,r,0,90); drawArc(x1,y3,r,180,270); drawArc(x3,y3,r,270,360);
}


void drawRoundedBox (FASTVAR word x, FASTVAR word y, word w, word h, FASTVAR word r) {
 FASTVAR word x1, y1, y2, x3, y3, w2;
 x1=x+r; y1=y+r; y2=y+h; x3=x+w-r; y3=y2-r; w2=w-r-r-1;
 drawBox(x,y1+1,w+1,h-r-r-1); drawBox(x1+1,y,w2,r+1); drawBox(x1+1,y3,w2,r+1);
 drawQuarterDisc(x1, y1, r, 1); drawQuarterDisc(x3, y1, r, 0); drawQuarterDisc(x1, y3, r, 2); drawQuarterDisc(x3, y3, r, 3);
}


static INLINE unsigned char getCharCode (FASTVAR unsigned char acode) { //get printable charcode from ASCII-code (convert accents)
 FASTVAR byte Code;
 static char noaccent[]={"__cLOYIS_C__-_R-__23_uS__10_____AAAAAAACEEEEIIIIDNOOOOOx0UUUUYPBaaaaaaaceeeeiiiidnooooo_0uuuuyPy"}; //optimized for ISO-8859-1

 if ( RARELY (acode < CHARCODE_MIN) ) Code = ' ';
 else if ( MOSTLY (acode<128) ) Code = acode - CHARCODE_MIN;
 else if ( TIGHTLY (acode>=160) ) Code = noaccent[acode-160] - CHARCODE_MIN;
 else Code = '_' - CHARCODE_MIN;

 return Code;
}


unsigned char drawFont (FASTVAR word x, FASTVAR word y, unsigned char acode) {
 FASTVAR word i; FASTVAR byte Fx,Fy,CharCode,Alpha;
 FASTVAR byte* FASTPTR FontPtr;
 CharCode = getCharCode(acode);
 FontPtr = FontSetPointer[CharCode]; if (FontPtr==NULL) return '_';
 for (i=Fx=Fy=0; Fy<FontHeight; ++i) {
  Alpha = FontPtr[i];  if ( RARELY (Alpha) ) drawAlphaPixel( x+Fx, y+Fy, byte2frac(Alpha) ); // /256.0 );
  if ( MOSTLY (Fx < FontWidthPointer[CharCode]-1) ) ++Fx;  else {Fx=0; ++Fy;}
 }
 return CharCode;
}


void drawString (FASTVAR word x, FASTVAR word y, FASTVAR unsigned char* FASTPTR str) {
 FASTVAR byte i, CharCode;
 for (i=0; str[i]!='\0'; ++i) {
  CharCode = drawFont(x, y, str[i]);
  x += FontWidthPointer[ CharCode ] + FONT_SPACING;
 }
}


void drawStringPart (FASTVAR word x, FASTVAR word y, FASTVAR word w, FASTVAR word count, FASTVAR unsigned char* FASTPTR str) {
 FASTVAR byte i, CharCode; FASTVAR word CharX, CharWidth;
 for (i=0, CharX=x; i<count && str[i]!='\0' /*&& str[i]!='\n'*/ && CharX+FontWidthPointer[getCharCode(str[i])]<x+w; ++i) {
  CharCode = drawFont( CharX, y, str[i] );
  CharX += FontWidthPointer[ CharCode ] + FONT_SPACING;
 }
 if (str[i]!='\0' && str[i]!='\n' && CharX+FontWidthPointer[getCharCode(str[i])]<x+w) drawFont(CharX,y,'.');
}
/*void drawStringPart (word x, word y, word count, unsigned char* str) {
 static byte i, CharCode;
 for (i=0; i<count && str[i]!='\0'; ++i) {
  CharCode = drawFont( x, y, str[i] );
  x += FontWidthPointer[ CharCode ] + FONT_SPACING;
 }
 if (str[i]!='\0') drawFont(x,y,'.');
}*/


word getTextWidth (FASTVAR char* FASTPTR str) {
 FASTVAR byte i; FASTVAR word x;
 for (i=x=0; str[i]!='\0'; ++i) {
  x += FontWidthPointer[ getCharCode(str[i]) ] + FONT_SPACING;
 }
 return x-FONT_SPACING;
}


void drawCenteredString (word x, word y, FASTVAR word w, word h, FASTVAR unsigned char* FASTPTR str) {
 FASTVAR byte i; FASTVAR word CharWidth, TextWidth;
 TextWidth=getTextWidth( (char*) str );
 if (TextWidth+BUTTONMARGIN*2 < w) drawString( x+float2int(w/2.0-TextWidth/2.0), y+float2int(h/2.0-FontHeight/2.0), str );
 else {
  for (i=TextWidth=0; str[i]!='\0'; ++i) {
   CharWidth = FontWidthPointer[ getCharCode(str[i]) ];
   if (TextWidth+CharWidth+BUTTONMARGIN*2+2 > w) break;
   //drawFont(x+TextWidth, y, str[i]);
   TextWidth += CharWidth + FONT_SPACING;
  }
  drawStringPart ( x+(w/2-(TextWidth-FONT_SPACING)/2), y+(h/2-FontHeight/2), w, i, str );
 }
}


void drawTextBox (FASTVAR word x, FASTVAR word y, FASTVAR word w, word h, FASTVAR unsigned char* FASTPTR str) {
 FASTVAR char End; FASTVAR int RowCount; FASTVAR unsigned char* FASTPTR NewLinePtr;
 RowCount=End=0;
 while (End==0) {
  NewLinePtr = (unsigned char*) strchr((char*)str, '\n'); if (NewLinePtr==NULL) { NewLinePtr = str+strlen((char*)str); End=1; }
  drawStringPart (x, y+RowCount*FontHeight, w, NewLinePtr-str, str ); ++RowCount; str = NewLinePtr+1;
 }
}

