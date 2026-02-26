/*
 * OpenMptDspEffects.h
 * -------------------
 * Purpose: Global DSP processing blocks inspired by OpenMPT desktop effects.
 *
 * Attribution:
 * Parts of the behavior and parameter model are adapted from the OpenMPT project
 * (https://openmpt.org), particularly sounddsp components (MegaBass, Reverb,
 * Surround, BitCrush).
 *
 * Licensing:
 * OpenMPT source code is BSD-3-Clause licensed. See LICENSE.OpenMPT in this
 * directory and upstream LICENSE for full terms.
 */

#ifndef SILICONPLAYER_OPENMPT_DSP_EFFECTS_H
#define SILICONPLAYER_OPENMPT_DSP_EFFECTS_H

#include <cstdint>
#include <array>
#include <vector>

namespace siliconplayer::effects {

struct OpenMptDspParams {
    bool bassEnabled = false;
    int bassDepth = 2;    // OpenMPT desktop slider step: 0..4
    int bassRange = 2;    // OpenMPT desktop slider step: 0..4

    bool surroundEnabled = false;
    int surroundDepth = 8;    // 1..16
    int surroundDelayMs = 20; // 5..45

    bool reverbEnabled = false;
    int reverbDepth = 8;  // 1..16
    int reverbPreset = 0; // 0..28

    bool bitCrushEnabled = false;
    int bitCrushBits = 16; // 1..24
};

class OpenMptDspEffects {
public:
    void reset();
    void process(float* interleavedBuffer, int frames, int channels, int sampleRate, const OpenMptDspParams& params);

private:
    static void shelfEq(
            int32_t scale,
            int32_t& outA1,
            int32_t& outB0,
            int32_t& outB1,
            int32_t fc,
            int32_t fs,
            float gainDC,
            float gainFT,
            float gainPI);
    void resetForSampleRate(int sampleRate);
    void applyBass(float* buffer, int frames, int channels, int sampleRate, const OpenMptDspParams& params);
    void applySurround(float* buffer, int frames, int channels, int sampleRate, const OpenMptDspParams& params);
    void applyReverb(float* buffer, int frames, int channels, int sampleRate, const OpenMptDspParams& params);
    void applyBitCrush(float* buffer, int frames, int channels, const OpenMptDspParams& params);
    void configureReverb(int sampleRate, int preset);
    void processReverbPreDelay(const int32_t* in, int32_t frames);
    void processReverbReflections(int32_t* out, int32_t frames);
    void processReverbLate(int32_t* out, int32_t frames);
    void processReverbPost(const int32_t* wet, int32_t* dry, int32_t frames);
    void applyReverbDryMix(int32_t* dry, const int32_t* wet, int32_t dryVol, int32_t frames);

    int configuredSampleRate = 0;
    int32_t bassX1 = 0;
    int32_t bassY1 = 0;
    int32_t bassDcrY1L = 0;
    int32_t bassDcrX1L = 0;
    int32_t bassDcrY1R = 0;
    int32_t bassDcrX1R = 0;

    std::vector<float> surroundDelayL;
    std::vector<float> surroundDelayR;
    int surroundWritePos = 0;
    int surroundConfiguredDelayMs = 20;
    int32_t surroundHpA1 = 0;
    int32_t surroundHpB0 = 0;
    int32_t surroundHpB1 = 0;
    int32_t surroundLpA1 = 0;
    int32_t surroundLpB0 = 0;
    int32_t surroundLpB1 = 0;
    int32_t surroundHpX1 = 0;
    int32_t surroundHpY1 = 0;
    int32_t surroundLpY1 = 0;
    int surroundConfiguredDepth = 8;
    float surroundOutputGain = 1.0f;

    int reverbConfiguredPreset = -1;
    int reverbConfiguredDepth = -1;
    int32_t reverbInputY1L = 0;
    int32_t reverbInputY1R = 0;
    int32_t reverbDcrX1L = 0;
    int32_t reverbDcrX1R = 0;
    int32_t reverbDcrY1L = 0;
    int32_t reverbDcrY1R = 0;

    int32_t reverbRefMasterGain = 0;
    int32_t reverbLateMasterGain = 0;
    int32_t reverbReflectionsGain = 0;
    uint32_t reverbPreDifPos = 0;
    uint32_t reverbDelayPos = 0;
    uint32_t reverbRefOutPos = 0;
    uint32_t reverbLateDelay = 0;
    uint32_t reverbLateDelayPos = 0;
    int16_t reverbRoomLpCoeffL = 0;
    int16_t reverbRoomLpCoeffR = 0;
    int16_t reverbRoomHistoryL = 0;
    int16_t reverbRoomHistoryR = 0;
    int16_t reverbPreDifCoeffL = 0;
    int16_t reverbPreDifCoeffR = 0;
    int16_t reverbDifCoeffL = 0;
    int16_t reverbDifCoeffR = 0;
    int16_t reverbDecayDcL = 0;
    int16_t reverbDecayDcR = 0;
    int16_t reverbDecayLpL = 0;
    int16_t reverbDecayLpR = 0;
    int16_t reverbLpHist0L = 0;
    int16_t reverbLpHist0R = 0;
    int16_t reverbLpHist1L = 0;
    int16_t reverbLpHist1R = 0;
    int16_t reverbDif2InGain0L = 0x7000;
    int16_t reverbDif2InGain0R = 0x1000;
    int16_t reverbDif2InGain1L = 0x1000;
    int16_t reverbDif2InGain1R = 0x7000;
    int16_t reverbOutGain0L = 0;
    int16_t reverbOutGain0R = 0;
    int16_t reverbOutGain1L = 0;
    int16_t reverbOutGain1R = 0;

    struct ReverbReflectionState {
        uint32_t delay = 0;
        int16_t gainLL = 0;
        int16_t gainRL = 0;
        int16_t gainLR = 0;
        int16_t gainRR = 0;
    };
    std::array<ReverbReflectionState, 8> reverbReflections {};

    std::vector<int16_t> reverbRefDelayBuffer;
    std::vector<int16_t> reverbPreDifBuffer;
    std::vector<int16_t> reverbRefOutBuffer;
    std::vector<int16_t> reverbDiffusion1;
    std::vector<int16_t> reverbDiffusion2;
    std::vector<int16_t> reverbDelay1;
    std::vector<int16_t> reverbDelay2;
    std::vector<int32_t> reverbWetWork;
    std::vector<int32_t> reverbDryWork;
};

} // namespace siliconplayer::effects

#endif // SILICONPLAYER_OPENMPT_DSP_EFFECTS_H
