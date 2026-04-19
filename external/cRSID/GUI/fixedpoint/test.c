
#include <stdio.h>


#include <math.h>


#include "../../libcRSID/Optimize.h"

#include "fixedpoint.c"


#define PI 3.14159 //22.0/7.0 = 3.14285
#define RAD (PI/180.0)


int frac2dec (FASTVAR fixed value) {
 enum { FRAC_DECIMALS_MUL = 1000000 };
 if (value == -256) return 0;
 else return (value * FRAC_DECIMALS_MUL) >> FIXEDPOINT_FRACTIONAL_SHIFTS;
}
int decFrac (fixed x) { return fixabs( frac2dec( frac(x) ) ); }
int decInt (fixed x) { return fixed2int(x); }
const char* decSign (fixed x) { return (x < 0 && !fixed2int(x)) ? "-" : ""; }


int main () {
 fixed A,B,C;
 float a,b,c;

 inline void makeFloats () { a=fixed2float(A); b=fixed2float(B); }; //c=fixed2float(C); }

 inline void printOperation (char* operator) {
  //makeFloats();
  printf( "%s%d,%6.6d %s %s%d,%6.6d = %s%d,%6.6d  (%f %s %f = %f)\n", decSign(A),decInt(A),decFrac(A), operator, decSign(B),decInt(B),decFrac(B), decSign(C),decInt(C),decFrac(C), a, operator, b, c );
 }

 A = FIXED(3.14159);  B = fixed2int(A);   C = int2fixed(B);   printf( "%s%d,%6.6d ($%X,$%X) -> %d -> %s%d,%6.6d ($%X)\n", decSign(A),decInt(A),decFrac(A),A,frac(A), B , decSign(C),decInt(C),decFrac(C),C );
 A = FIXED(-3.14159); B = fixed2int(A);   C = int2fixed(B);   printf( "%s%d,%6.6d ($%X,$%X) -> %d -> %s%d,%6.6d ($%X)\n", decSign(A),decInt(A),decFrac(A),A,frac(A), B , decSign(C),decInt(C),decFrac(C),C );
 a = 3.14159;         A = float2fixed(a); b = fixed2float(A); printf( "%f -> %s%d,%6.6d -> %f\n", a, decSign(A),decInt(A),decFrac(A) , b );
 A = FIXED(3.14159);  a = fixed2float(A); B = float2fixed(a); printf( "%s%d,%6.6d -> %f -> %s%d,%6.6d\n", decSign(A),decInt(A),decFrac(A), a , decSign(B),decInt(B),decFrac(B) );
 a = -3.14159;        A = float2fixed(a); b = fixed2float(A); printf( "%f -> %s%d,%6.6d -> %f\n", a, decSign(A),decInt(A),decFrac(A) , b );
 A = FIXED(-3.14159); a = fixed2float(A); B = float2fixed(a); printf( "%s%d,%6.6d -> %f -> %s%d,%6.6d\n", decSign(A),decInt(A),decFrac(A), a , decSign(B),decInt(B),decFrac(B) );
 printf("\n");
 A = FIXED(3.14159);  B = FIXED(928.3253); C = fixadd(A,B); makeFloats(); c=a+b; printOperation("+");
 A = FIXED(928.3253); B = FIXED(3.14159);  C = fixsub(A,B); makeFloats(); c=a-b; printOperation("-");
 A = FIXED(0.5);      B = FIXED(0.5);      C = fixmul(A,B); makeFloats(); c=a*b; printOperation("*");
 A = FIXED(3.14159);  B = FIXED(928.3253); C = fixmul(A,B); makeFloats(); c=a*b; printOperation("*");
 A = FIXED(3.14159);  B = FIXED(-28.33);   C = fixmul(A,B); makeFloats(); c=a*b; printOperation("*");
 A = FIXED(-3.14159); B = FIXED(-28.33);   C = fixmul(A,B); makeFloats(); c=a*b; printOperation("*");
 A = FIXED(928.3253); B = FIXED(3.14159);  C = fixdiv(A,B); makeFloats(); c=a/b; printOperation("/");
 A = FIXED(928.3253); B = FIXED(-3.14159); C = fixdiv(A,B); makeFloats(); c=a/b; printOperation("/");
 A = FIXED(-928.3253);B = FIXED(3.14159);  C = fixdiv(A,B); makeFloats(); c=a/b; printOperation("/");
 A = FIXED(-1.0);     B = FIXED(3.14159);  C = fixdiv(A,B); makeFloats(); c=a/b; printOperation("/");
 A = FIXED(-1.0);     B = FIXED(-3.14159); C = fixdiv(A,B); makeFloats(); c=a/b; printOperation("/");
 A = FIXED(-0.894);   B = FIXED(-3.14159); C = fixdiv(A,B); makeFloats(); c=a/b; printOperation("/");
 printf("\n");
 A = FIXED(-72.4); B = fixsqr(A); makeFloats(); c = a * a; printf( "fixsqr(%s%d,%6.6d) = %s%d,%6.6d  (%f * %f = %f)\n", decSign(A),decInt(A),decFrac(A), decSign(B),decInt(B),decFrac(B), a,a, c );
 A = 7; B = 11; C = intpow(A,B); makeFloats(); c=pow((float)A,(float)B); printf( "%d ** %d = %d  (%f ** %f = %f)\n", A, B, C, (float)A, (float)B, c );
 A = 7; B = 11; C = intpowBitwise(A,B); makeFloats(); c=pow((float)A,(float)B); printf( "bitwise: %d ** %d = %d  (%f ** %f = %f)\n", A, B, C, (float)A, (float)B, c );
 //A = 3; B = FIXED(1.0/(1<<8)); C = fixpow(A,B); makeFloats(); c=pow((float)A,b); printf( "%d ** %s%d,%6.6d = %s%d,%6.6d  (%f ** %f = %f)\n", A, decSign(B),decInt(B),decFrac(B), decSign(C),decInt(C),decFrac(C), (float)A, b, c );
 //A = FIXED(3.14159); B = FIXED(8.4567); C = fixpow(A,B); makeFloats(); c=pow(a,b); printOperation("**");
 printf("\n");
 A = 2147483647 /*(1<<32)-1*/ /*max*/; C = intsqrt(A); c=sqrt((double)A); printf( "intsqrt( %d ) = %d  ( sqrt(%f) = %f )\n", A, C, (double)A, c );
 A = 2147483647 /*(1<<32)-1*/ /*max*/; C = intsqrt2(A); c=sqrt((double)A); printf( "intsqrt2( %d ) = %d  ( sqrt(%f) = %f )\n", A, C, (double)A, c );
 A = 2147483647 /*4294967295*/ /*max*/; C = intsqrtBitwise(A); c=sqrt((double)A); printf( "intsqrtBitwise( %d ) = %d  ( sqrt(%f ) = %f)\n", A, C, (double)A, c );
 //A = 2147483647 /*max*/; C = intsqrtNewton(A); c=sqrt((double)A); printf( "intsqrtNewton( %d ) = %d  ( sqrt(%f) = %f )\n", A, C, (double)A, c );
 //A = 2147483647 /*4294967295*/ /*max*/; C = intsqrtBo(A); c=sqrt((double)A); printf( "intsqrtBo( %d ) = %d  ( sqrt(%f) = %f )\n", A, C, (double)A, c );
 //A = 452027485 /*92681*/ /*max*/; C = intsqrtBinSearch(A); c=sqrt((float)A); printf( "intsqrtBinSearch( %d ) = %d  ( sqrt(%f) = %f )\n", A, C, (float)A, c );
 A = FIXED( 8388607 ) /*max*/; C = fixsqrt(A); makeFloats(); c=sqrt(a); printf( "fixsqrt( %s%d,%6.6d ) = %s%d,%6.6d  ( sqrt(%f) = %f )\n", decSign(A),decInt(A),decFrac(A), decSign(C),decInt(C),decFrac(C), a, c );
 A = FIXED( 8388607 ) /*max*/; C = fixsqrt2(A); makeFloats(); c=sqrt(a); printf( "fixsqrt2( %s%d,%6.6d ) = %s%d,%6.6d  ( sqrt(%f) = %f )\n", decSign(A),decInt(A),decFrac(A), decSign(C),decInt(C),decFrac(C), a, c );
 printf("\n");
 float Angle = -233485.7;
 A = FIXED( Angle ); C = fixsin(A); makeFloats(); c=sin(a*RAD); printf( "fixsin( %s%d,%6.6d ) = %s%d,%6.6d ($%X)  ( sin(%f) = %f )\n", decSign(A),decInt(A),decFrac(A), decSign(C),decInt(C),decFrac(C),C, a, c );
 A = FIXED( Angle ); C = fixcos(A); makeFloats(); c=cos(a*RAD); printf( "fixcos( %s%d,%6.6d ) = %s%d,%6.6d ($%X)  ( cos(%f) = %f )\n", decSign(A),decInt(A),decFrac(A), decSign(C),decInt(C),decFrac(C),C, a, c );

 return 0;
}

