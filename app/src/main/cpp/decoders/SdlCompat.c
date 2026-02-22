#include <pthread.h>
#include <stdint.h>
#include <stddef.h>
#include <stdlib.h>
#include <dlfcn.h>

typedef struct SDL_mutex {
    pthread_mutex_t mutex;
} SDL_mutex;

typedef struct SDL_RWops SDL_RWops;

// klystron can be built either with its internal RWops layout (read callback
// at offset 0) or with SDL-style RWops (read callback at offset 16 on 64-bit).
// We support both to keep Android linkage/runtime stable.
typedef int (*SDL1ReadFn)(SDL_RWops* context, void* ptr, int size, int maxnum);
typedef size_t (*SDL2ReadFn)(SDL_RWops* context, void* ptr, size_t size, size_t maxnum);

struct SDL1RWopsHead {
    SDL1ReadFn read_fn;
    int (*close_fn)(SDL_RWops* context);
};

struct SDL2RWopsHead {
    void* size_fn;
    void* seek_fn;
    SDL2ReadFn read_fn;
};

static int is_probable_code_ptr(const void* ptr) {
    if (!ptr) return 0;
    Dl_info info;
    return dladdr(ptr, &info) != 0;
}

SDL_mutex* SDL_CreateMutex(void) {
    SDL_mutex* handle = (SDL_mutex*)malloc(sizeof(SDL_mutex));
    if (!handle) return NULL;
    if (pthread_mutex_init(&handle->mutex, NULL) != 0) {
        free(handle);
        return NULL;
    }
    return handle;
}

void SDL_DestroyMutex(SDL_mutex* mutex) {
    if (!mutex) return;
    pthread_mutex_destroy(&mutex->mutex);
    free(mutex);
}

int SDL_LockMutex(SDL_mutex* mutex) {
    if (!mutex) return -1;
    return pthread_mutex_lock(&mutex->mutex);
}

int SDL_UnlockMutex(SDL_mutex* mutex) {
    if (!mutex) return -1;
    return pthread_mutex_unlock(&mutex->mutex);
}

size_t SDL_RWread(SDL_RWops* context, void* ptr, size_t size, size_t maxnum) {
    if (!context || !ptr || size == 0 || maxnum == 0) return 0;

    // Detect SDL2-like RWops first when a valid code pointer exists at the
    // SDL2 read callback slot.
    const struct SDL2RWopsHead* sdl2 = (const struct SDL2RWopsHead*)context;
    if (sdl2->read_fn && is_probable_code_ptr((const void*)sdl2->read_fn)) {
        return sdl2->read_fn(context, ptr, size, maxnum);
    }

    // Fallback to klystron/SDL1-like layout where read callback is the first
    // field and takes int size/count parameters.
    const struct SDL1RWopsHead* sdl1 = (const struct SDL1RWopsHead*)context;
    if (!sdl1->read_fn) return 0;
    int read_items = sdl1->read_fn(context, ptr, (int)size, (int)maxnum);
    if (read_items <= 0) return 0;
    return (size_t)read_items;
}

int SDL_OpenAudio(void* desired, void* obtained) {
    (void)desired;
    (void)obtained;
    return -1;
}

void SDL_PauseAudio(int pauseOn) {
    (void)pauseOn;
}

void SDL_CloseAudio(void) {
}
