//Small fixed-point arithmetic library by Hermit for faster anti-aliased alpha-pixelling and
//curve/polygon drawing on processors that don't have fast hardware-based floating point arithmetic support.
//Uses Q16.8 format with MSB as sign-bit, which is adequate on 32-bit systems for coordinate/color calculations
//(The code uses arithmetic right-sift instead of division on signed integers which some platforms might implement differently.)


#include "fixedpoint.h"

#include "tables.c"



static INLINE int float2int (FASTVAR float value) {
 return (int)value + ( (value-(int)value) >= 0.5 );
}
static INLINE fixed float2fixed (FASTVAR float value) {
 return (fixed) ( value * FIXEDPOINT_FRACTIONAL_RATIO );  // + (value >= 0 ? +0.5 : -0.5) )
}
static INLINE float fixed2float (FASTVAR fixed value) { //mainly for testing reasons
 return (float) ( value / (float)FIXEDPOINT_FRACTIONAL_RATIO );
}
static INLINE fixed int2fixed (FASTVAR int value) {
 return /*(fixed)*/ (value << FIXEDPOINT_FRACTIONAL_SHIFTS);
}
static INLINE fixed char2fixed (FASTVAR char value) {
 return /*(fixed)*/ (value << FIXEDPOINT_FRACTIONAL_SHIFTS);
}
static INLINE fixed byte2fixed (FASTVAR byte value) {
 return /*(fixed)*/ (value << FIXEDPOINT_FRACTIONAL_SHIFTS);
}
static INLINE fixed word2fixed (FASTVAR word value) {
 return /*(fixed)*/ (value << FIXEDPOINT_FRACTIONAL_SHIFTS);
}
static INLINE fixed u32toFixed (FASTVAR u32 value) {
 return /*(fixed)*/ (value << FIXEDPOINT_FRACTIONAL_SHIFTS);
}
static INLINE int fixed2int (FASTVAR fixed value) {
 enum { FIXEDPOINT_TO_INT__ADD = (-1 + FIXEDPOINT_VALUE_1) };
 if ( MOSTLY (value >= 0) ) return /*(int)*/ (value >> FIXEDPOINT_FRACTIONAL_SHIFTS);
 else return /*(int)*/ ( (value + FIXEDPOINT_TO_INT__ADD) >> FIXEDPOINT_FRACTIONAL_SHIFTS );
}
static INLINE word fixed2word (FASTVAR fixed value) {
 return /*(word)*/ (value >> FIXEDPOINT_FRACTIONAL_SHIFTS);
}
static INLINE u32 fixed2u32 (FASTVAR fixed value) {
 return /*(u32)*/ (value >> FIXEDPOINT_FRACTIONAL_SHIFTS);
}



static INLINE fixed frac (FASTVAR fixed value) {
 if (value >= 0) return value & FIXEDPOINT_FRACTIONAL_ANDMASK;
 else return FIXEDPOINT_VALUE_MINUS_1 + (value & FIXEDPOINT_FRACTIONAL_ANDMASK);
}

static INLINE fixed byte2frac (FASTVAR byte value) { return /*(fixed)*/ value; } //handle 0..255 as 0.0 ... 0.996 from now on

static INLINE fixed fracFloat (FASTVAR float value) { return float2fixed(value) & FIXEDPOINT_FRACTIONAL_ANDMASK; }


static INLINE fixed fixadd (FASTVAR fixed value1, FASTVAR fixed value2) { return value1 + value2; }

static INLINE fixed fixsub (FASTVAR fixed value1, FASTVAR fixed value2) { return value1 - value2; }


static INLINE fixed fixmul (FASTVAR fixed value, FASTVAR fixed multiplier) {
 return (value * multiplier) >> FIXEDPOINT_FRACTIONAL_SHIFTS; // / FIXEDPOINT_FRACTIONAL_RATIO;
}
static INLINE fixed fmulFloat (FASTVAR float value, FASTVAR fixed multiplier) {
 return ( float2fixed(value) * multiplier ) >> FIXEDPOINT_FRACTIONAL_SHIFTS; // / FIXEDPOINT_FRACTIONAL_RATIO;
}
static INLINE int fmul2int (FASTVAR int value, FASTVAR fixed multiplier) {
 return (value * multiplier) >> FIXEDPOINT_FRACTIONAL_SHIFTS; // / FIXEDPOINT_FRACTIONAL_RATIO;
}
static INLINE byte fmul2byte (FASTVAR int value, FASTVAR fixed multiplier) {
 return (value * multiplier) >> FIXEDPOINT_FRACTIONAL_SHIFTS; // / FIXEDPOINT_FRACTIONAL_RATIO;
}


static INLINE fixed fixdiv (FASTVAR fixed value, FASTVAR fixed divisor) {
 return (value << FIXEDPOINT_FRACTIONAL_SHIFTS) / divisor;
}
static INLINE fixed fdivWord (FASTVAR word value, FASTVAR word divisor) {
 return (value << FIXEDPOINT_FRACTIONAL_SHIFTS) / divisor;
}
static INLINE fixed fdivByWord (FASTVAR fixed value, FASTVAR word divisor) {
 return value / divisor;
}


static INLINE s32 intabs (FASTVAR s32 value) {
 return value >= 0 ? value : -value;
}
static INLINE fixed fixabs (FASTVAR fixed value) {
 return value >= 0 ? value : -value;
}
static INLINE fixed fabsInt (FASTVAR int value) {
 return int2fixed( value >= 0 ? value : -value );
}



static INLINE u32 bitCount (FASTVAR u32 value) { //gets magnitude of the number
 FASTVAR int i;
 for (i=FIXEDPOINT_WORD_BITS; i >= 0; --i) {
  if (value&FIXEDPOINT_WORD_MSB_VALUE) return i;
  value <<= 1;
 }
 return 0;
}



static INLINE fixed fixsqr (FASTVAR fixed value) { return fixmul( value, value ); }


static INLINE s32 intpow (FASTVAR s32 base, FASTVAR u8 power) { //Hermit's simple method for small numbers (result can't be bigger than 32bit anyway)
 if (power==0) return 1;   FASTVAR s32 Result = base;   while (--power) Result *= base;   return Result;
}


static INLINE s32 intpowBitwise (FASTVAR s32 base, FASTVAR u32 power) { //bitwise exponentiation by squaring (only positive power)
 FASTVAR s32 Result = 1; //if (power < 0) { base = 1.0 / base; power = -power; }  else if (power==0) return 1; //no point for 1/base with power<0, it can be done externally by simple division, and handling power=0 is only an optimization
 while (power) {
  if (power&1) Result = (Result * base);
  power >>= 1; base = (base * base);    //printf("%d,%d,%d\n",base,power,Result);
 }
 return Result;
} //btw x ** y = exp( y * log(x) )



static INLINE u32 intsqrt (FASTVAR u32 value) { //Hermit's own fast byte-based method using 256byte lookup-table and linear interpolation
 //Eliminating branches completely (might be faster if compiler and CPU is not good with branch-predictions. But shifting negatives might be incompatible on some system...)
 FASTVAR u32 Shifts, TableShifts, Index, ValueL;   //If8,If16,If24 //,ValueMask,DiffMask;  //(Not precise to the smallest bit (result divergences between 1..5), but should be very fast with balanced speed.)
 static const u8 ShiftTable [] = { 0, 24, (16),16, 24,(24), (8),8, 24, (8), 16, 24 };   //If8 = ( -((s32)value & 0xFF00) >> 31 ); If16 = ( -((s32)value & 0xFF0000) >> 31 ); If24 = ( -((s32)value & 0x7F000000) >> 31 );
 Shifts = ShiftTable[ (-(value & 0xFF00) >> 29) + (-(value & 0xFF0000) >> 30) + (-(value & 0x7F000000) >> 31) ];   //Shifts = ( ((If8 & ~If16) | If24) & 0x08 ) | ( (If16 | If24) & 0x10 );
 Index = value >> Shifts; TableShifts = 12 - (Shifts >> 1);   //ValueMask = (1 << Shifts) - 1; DiffMask = (0x10 << (Shifts>>1)) - 1;
 ValueL = ( FIXEDPOINT_SquareRootTable[Index] << 8 ) >> TableShifts;
 return ValueL + (
  (
   ( value & ((1 << Shifts) - 1) ) *
   ( ( ((FIXEDPOINT_SquareRootTable[Index+1]<<8) >> TableShifts) - ValueL ) & ( (0x10 << (Shifts>>1)) - 1 ) )  //(value & ValueMask) * ( ( ((FIXEDPOINT_SquareRootTable[Index+1]<<8) >> TableShifts) - ValueL ) & DiffMask )
  ) >> Shifts
 );
}

static INLINE u32 intsqrt2 (FASTVAR u32 value) { //Hermit's own fast byte-based method using 256byte lookup-table and linear interpolation
 FASTVAR u32 Index, ValueL;  //(Not precise to the smallest bit (result divergences between 1..5), but should be very fast with balanced speed.)
 if ( TIGHTLY (value >= 0x10000) ) {
  if ( TIGHTLY (value >= 0x1000000) ) {
   Index = (value >> 24); ValueL = FIXEDPOINT_SquareRootTable[Index] << 8;
   return ValueL + ( ( (value & 0xFFFFFF) * (((FIXEDPOINT_SquareRootTable[Index+1] << 8) - ValueL) & 0xFFFF) ) >> 24 );
  }
  else {
   Index = (value >> 16); ValueL = FIXEDPOINT_SquareRootTable[Index] << 4;
   return ValueL + ( ( (value & 0xFFFF) * (((FIXEDPOINT_SquareRootTable[Index+1] << 4) - ValueL) & 0xFFF) ) >> 16 );
 }}
 else {
  if (TIGHTLY (value >= 0x100) ) {
   Index = (value >> 8); ValueL = FIXEDPOINT_SquareRootTable[Index];
   return ValueL + ( ( (value & 0xFF) * ((FIXEDPOINT_SquareRootTable[Index+1] - ValueL) & 0xFF) ) >> 8 );
  }
  return FIXEDPOINT_SquareRootTable[ value & 0xFF ] >> 4; //FIXEDPOINT_TABLEVALUE_FRACTIONAL_BITS;
 }
}


static INLINE u32 intsqrtBitwise (FASTVAR u32 value) { //bitwise square-root (fewer predictable amount of iterations)
 //if (value < 0) return -1; square-root of negative is only meaningful for complex number imaginary part
 FASTVAR u32 Result = 0, Bit = 1 << ( (bitCount(value) / 2) * 2 ); //this could start at first nonzero position to reduce main iterations
 while (Bit > 0) {
  if (value >= Result + Bit) { value -= Result+Bit; Result = (Result >> 1) + Bit; }  else Result >>= 1;
  Bit >>= 2;    //printf("%d,%d,%d\n",Bit,Result,value); //with unsigned 32bit, input value can be max. 4294967295 (max. 16 iterations)
 }
 return Result;  //if Result * Result == OriginalValue it was a perfect square
} //btw sqrt(x) = pow( x, 0.5 )  (easy if float-number representation is know, just have to halve its exponent-part, fast inverse squareroot method in Doom3 or GCC with -O3 might use similar technique)


/*static INLINE u32 intsqrtNewton (FASTVAR u32 value) { //Newtinian/Babilonian Square-root method (successively faster convergence)
 //if (value < 0) return -1; square-root of negative is only meaningful for complex number imaginary part
 FASTVAR u32 X = value, Y = (X + value / X) / 2;  //division might be slower, with signed 32bit integer, max input value is 2147483647*2 (20 iterations)
 while (Y < X) { X = Y; Y = (X + value / X) / 2; } //printf("%d,%d,%d\n",X,Y,value); }
 return X;  //if X * X == value it was a perfect square
}


static INLINE u32 intsqrtBo_estimate (FASTVAR u32 value) {
 FASTVAR u32 M, X;
 M = bitCount( value ); M = ( (M + 1) >> 1 ) << 1; M -= 8;  X = (value >> M);   // X is the most-significant 8 bits here
 X = ( FIXEDPOINT_SquareRootTable[ X ] >> FIXEDPOINT_TABLEVALUE_FRACTIONAL_BITS ) << (M >> 1);   printf("%d,%d\n", M, X);
 return X;
}
static INLINE u32 intsqrtBo (FASTVAR u32 value) { //Bo's estimate-table based Square-root method (very few iterations)
 FASTVAR u32 Upper = 0, Lower, X, DoubleX; FASTVAR s32 Remainder; //with integers LSB can be imprecise (rounding upwards)
 if (value < FIXEDPOINT_TABLEVALUE_RANGE) return (FIXEDPOINT_SquareRootTable[ value ] >> FIXEDPOINT_TABLEVALUE_FRACTIONAL_BITS);
 X = intsqrtBo_estimate( value ); //if (value < FIXEDPOINT_TABLEVALUE_RANGE) return X; //this sis incorrect for value<64
 Remainder = value - X*X;
 while (Remainder > 0) { //printf("%d,%d,%d,%d\n", Upper, Lower, Remainder, X);
  DoubleX = 2 * X; Upper = Remainder / DoubleX; Lower = (Remainder - 1) / (DoubleX + Upper) + 1;  //same as ceil( Remainder / (DoubleX + Upper) )
   printf("%d,%d,%d,%d\n", Upper, Lower, Remainder, X);
  if (Upper < Lower) return X+Lower; //-1;
  Remainder -= (DoubleX + Lower) * Lower; X += Lower;
 }
 return X;
}


static INLINE u32 intsqrtBinSearch (FASTVAR u32 value) { //binary-search square-root (most iterations needed)
 //if (value < 0) return -1; square-root of negative is only meaningful for complex number imaginary part
 FASTVAR u32 Low = 0, High = value, Mid; FASTVAR u32 Square;
 while (Low <= High) {
  Mid = (Low + High) / 2; Square = Mid * Mid;  //an uint32_t Square limits input to 452027485 (29 iterations), a signed 32bit int to 92681
  if (Square == value) return Mid; else if (Square < value) Low = Mid + 1; else High = Mid - 1;     //printf("%d,%d,%d,%d\n",Low,High,Mid,Square);
 }
 return High; //Mid;  //High returns the floor value of float equivalent
}*/



static INLINE ufixed fixsqrt (FASTVAR ufixed value) {
 return intsqrt( value ) << (FIXEDPOINT_FRACTIONAL_SHIFTS >> 1);
}
static INLINE ufixed fixsqrt2 (FASTVAR ufixed value) {
 return intsqrt2( value ) << (FIXEDPOINT_FRACTIONAL_SHIFTS >> 1);
}

static INLINE float fixsqrt2float (FASTVAR fixed value) {
 return fixed2float( intsqrt( value ) << (FIXEDPOINT_FRACTIONAL_SHIFTS >> 1) );
}



static INLINE fixed fixsin (FASTVAR fixed angle) {
 enum { ANGLE_MAX = (FIXEDPOINT_SINE_TABLE_SIZE-1), ANGLE_RANGE = FIXED(FIXEDPOINT_SINE_TABLE_SIZE) };
 angle = (angle % ANGLE_RANGE) + (angle >= 0 ? 0:ANGLE_RANGE); //(maybe modulo is slower on ARMel GCC output and have to make a faster custom one..)
 if (angle < FIXED(90)) return FIXEDPOINT_SineTable[ (angle<<2) >> FIXEDPOINT_FRACTIONAL_SHIFTS ];
 else if (angle < FIXED(180)) return FIXEDPOINT_SineTable[ ANGLE_MAX - ( ((angle-FIXED(90))<<2) >> FIXEDPOINT_FRACTIONAL_SHIFTS ) ];
 else if (angle < FIXED(270)) return -FIXEDPOINT_SineTable[ ((angle-FIXED(180))<<2) >> FIXEDPOINT_FRACTIONAL_SHIFTS ];
 else return -FIXEDPOINT_SineTable[ ANGLE_MAX - ( ((angle-FIXED(270))<<2) >> FIXEDPOINT_FRACTIONAL_SHIFTS ) ];
 return 0;
} //sin(x) = sign * x * (pi - x*x * ( two_pi - 5 - x*x * (pi - 3) ) ) / 2   //sin(x) = sign * x * (1.0 + x*x * ( (x*x * 0.00761) - 0.16605 ) )


static INLINE fixed fixcos (FASTVAR fixed angle) {
 return fixsin( angle + FIXED(90) );
}

