#ifndef SILICONPLAYER_RESIDEMU_H
#define SILICONPLAYER_RESIDEMU_H

#include <cstdint>

#ifndef HAVE_CXX11
#define HAVE_CXX11 1
#endif

#include "Event.h"
#include "sidemu.h"
#include <resid/sid.h>

class sidbuilder;

namespace libsidplayfp {

class ReSidEmu final : public sidemu {
public:
    static const char* getCredits();

    explicit ReSidEmu(sidbuilder* builder);
    ~ReSidEmu() override;

    uint8_t read(uint_least8_t addr) override;
    void write(uint_least8_t addr, uint8_t data) override;
    void reset(uint8_t volume) override;
    void clock() override;
    void sampling(float systemclock, float freq, SidConfig::sampling_method_t method) override;
    void model(SidConfig::sid_model_t model, bool digiboost) override;

    void bias(double dacBias);
    void filter(bool enable);

private:
    bool applySamplingConfiguration();

    reSID::SID& sid;
    int bufferSize = 0;
    uint8_t voiceMask = 0x07;
    SidConfig::sid_model_t currentModel = SidConfig::MOS8580;
    float lastSystemClockHz = 0.0f;
    float lastSampleRateHz = 0.0f;
    SidConfig::sampling_method_t requestedSampling = SidConfig::INTERPOLATE;
};

} // namespace libsidplayfp

#endif // SILICONPLAYER_RESIDEMU_H
