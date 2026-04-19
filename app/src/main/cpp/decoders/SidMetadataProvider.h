#ifndef SILICONPLAYER_SIDMETADATAPROVIDER_H
#define SILICONPLAYER_SIDMETADATAPROVIDER_H

#include <string>

class SidMetadataProvider {
public:
    virtual ~SidMetadataProvider() = default;

    virtual std::string getSidFormatName() = 0;
    virtual std::string getSidClockName() = 0;
    virtual std::string getSidSpeedName() = 0;
    virtual std::string getSidCompatibilityName() = 0;
    virtual std::string getSidBackendName() = 0;
    virtual int getSidChipCountInfo() = 0;
    virtual std::string getSidModelSummary() = 0;
    virtual std::string getSidCurrentModelSummary() = 0;
    virtual std::string getSidBaseAddressSummary() = 0;
    virtual std::string getSidCommentSummary() = 0;
};

#endif // SILICONPLAYER_SIDMETADATAPROVIDER_H
