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
    // Allocate a dummy CPCONV structure so the pointer is not NULL
    // This is required because libvgm checks if the pointer is NULL before converting strings
    *retCPC = (CPCONV*)malloc(sizeof(CPCONV));
    if (*retCPC == NULL) {
        return 0xFF;  // Allocation failed
    }
    (*retCPC)->dummy = 0;
    return 0x00;  // Success
}

// Deinitialize codepage conversion (stub - free the dummy structure)
void CPConv_Deinit(CPCONV* cpc) {
    if (cpc != NULL) {
        free(cpc);
    }
}

// Convert string (UTF-16LE to UTF-8 conversion)
UINT8 CPConv_StrConvert(CPCONV* cpc, size_t* outSize, char** outStr, size_t inSize, const char* inStr) {
    if (outStr == NULL || inStr == NULL) {
        return 0xFF;  // Error
    }

    // Simple UTF-16LE to UTF-8 conversion
    // This handles basic ASCII and common characters, but not all Unicode
    const unsigned short* utf16 = (const unsigned short*)inStr;
    size_t utf16Len = inSize / 2;

    // Allocate maximum possible size (each UTF-16 char could become 3 UTF-8 bytes)
    char* utf8 = (char*)malloc(utf16Len * 3 + 1);
    if (utf8 == NULL) {
        return 0xFF;  // Allocation failed
    }

    size_t utf8Pos = 0;
    for (size_t i = 0; i < utf16Len; i++) {
        unsigned short ch = utf16[i];

        if (ch == 0) {
            break;  // Null terminator
        } else if (ch < 0x80) {
            // ASCII (0x00-0x7F)
            utf8[utf8Pos++] = (char)ch;
        } else if (ch < 0x800) {
            // 2-byte UTF-8 (0x80-0x7FF)
            utf8[utf8Pos++] = (char)(0xC0 | (ch >> 6));
            utf8[utf8Pos++] = (char)(0x80 | (ch & 0x3F));
        } else {
            // 3-byte UTF-8 (0x800-0xFFFF)
            utf8[utf8Pos++] = (char)(0xE0 | (ch >> 12));
            utf8[utf8Pos++] = (char)(0x80 | ((ch >> 6) & 0x3F));
            utf8[utf8Pos++] = (char)(0x80 | (ch & 0x3F));
        }
    }

    utf8[utf8Pos] = '\0';
    *outStr = utf8;

    if (outSize != NULL) {
        *outSize = utf8Pos;
    }

    return 0x00;  // Success
}
