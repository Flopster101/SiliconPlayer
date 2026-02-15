// Stub implementation of CPConv functions for libvgm when charset conversion is disabled
// VGM metadata is typically ASCII or UTF-8, so we can just pass through the strings

#include <stdlib.h>
#include <string.h>

typedef struct _codepage_conversion {
    int dummy;
} CPCONV;

typedef unsigned char UINT8;

// Initialize codepage conversion (stub - always succeeds)
UINT8 CPConv_Init(CPCONV** retCPC, const char* cpFrom, const char* cpTo) {
    *retCPC = NULL;  // No conversion needed
    return 0x00;  // Success
}

// Deinitialize codepage conversion (stub - no-op)
void CPConv_Deinit(CPCONV* cpc) {
    // Nothing to do
}

// Convert string (stub - just copy the input to output)
UINT8 CPConv_StrConvert(CPCONV* cpc, size_t* outSize, char** outStr, size_t inSize, const char* inStr) {
    if (outStr == NULL || inStr == NULL) {
        return 0xFF;  // Error
    }

    // Allocate output buffer and copy input
    *outStr = (char*)malloc(inSize + 1);
    if (*outStr == NULL) {
        return 0xFF;  // Allocation failed
    }

    memcpy(*outStr, inStr, inSize);
    (*outStr)[inSize] = '\0';

    if (outSize != NULL) {
        *outSize = inSize;
    }

    return 0x00;  // Success
}
