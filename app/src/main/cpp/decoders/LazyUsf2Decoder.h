#ifndef SILICONPLAYER_LAZYUSF2DECODER_H
#define SILICONPLAYER_LAZYUSF2DECODER_H

#include "AudioDecoder.h"
#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

class LazyUsf2Decoder : public AudioDecoder {
public:
    LazyUsf2Decoder();
    ~LazyUsf2Decoder() override;

    bool open(const char* path) override;
    void close() override;
    int read(float* buffer, int numFrames) override;
    void seek(double seconds) override;
    double getDuration() override;
    int getSampleRate() override;
    int getBitDepth() override;
    std::string getBitDepthLabel() override;
    int getChannelCount() override;
    std::string getTitle() override;
    std::string getArtist() override;
    std::string getComposer() override;
    std::string getGenre() override;
    std::string getGameName();
    std::string getCopyright();
    std::string getYear();
    std::string getUsfBy();
    std::string getLengthTag();
    std::string getFadeTag();
    bool getEnableCompare();
    bool getEnableFifoFull();
    void setOutputSampleRate(int sampleRate) override;
    void setRepeatMode(int mode) override;
    int getRepeatModeCapabilities() const override;
    int getPlaybackCapabilities() const override;
    double getPlaybackPositionSeconds() override;
    TimelineMode getTimelineMode() const override { return TimelineMode::ContinuousLinear; }

    const char* getName() const override { return "LazyUSF2"; }
    static std::vector<std::string> getSupportedExtensions();

private:
    struct ParsedPsf {
        std::vector<uint8_t> reservedSection;
        std::unordered_map<std::string, std::string> tags;
    };

    mutable std::mutex decodeMutex;
    std::vector<uint8_t> emulatorState;
    void* state = nullptr;
    bool isOpen = false;
    bool needsShutdown = false;

    int outputSampleRate = 48000;
    int channels = 2;
    int bitDepth = 16;
    double durationSeconds = 0.0;
    bool durationReliable = false;
    std::atomic<int> repeatMode { 0 };
    int64_t renderedFrames = 0;

    bool enableCompare = false;
    bool enableFifoFull = false;

    std::string title;
    std::string artist;
    std::string composer;
    std::string genre;
    std::string gameName;
    std::string copyrightText;
    std::string year;
    std::string usfBy;
    std::string lengthTag;
    std::string fadeTag;
    std::string sourcePath;

    void closeInternal();
    bool loadPsfTree(const std::string& path);
    bool loadPsfRecursive(
            const std::string& path,
            std::unordered_set<std::string>& recursionStack,
            std::unordered_set<std::string>& loadedPaths,
            bool metadataAllowed
    );
    bool parsePsfFile(const std::string& path, ParsedPsf& parsed) const;
    bool applyMetadataFromTags(const std::unordered_map<std::string, std::string>& tags);
    void applyCoreTags(const std::unordered_map<std::string, std::string>& tags);
    void seekInternalLocked(double seconds);

    static std::string toLowerAscii(std::string value);
    static std::string trimAscii(std::string value);
    static std::string normalizePath(const std::string& value);
    static std::string resolveRelativePath(const std::string& baseFilePath, const std::string& relative);
    static bool parseBoolTag(const std::string& value);
    static unsigned long parsePsfTimeMs(const std::string& input, bool& ok);
    static uint32_t readLe32(const uint8_t* data);
};

#endif // SILICONPLAYER_LAZYUSF2DECODER_H
