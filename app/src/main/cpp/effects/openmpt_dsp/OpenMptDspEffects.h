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
    int bassDepth = 6;    // OpenMPT-like range 4..8
    int bassRange = 14;   // OpenMPT-like range 5..21

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

    int configuredSampleRate = 0;

    std::array<float, 2> bassLpState { 0.0f, 0.0f };

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

    std::vector<float> reverbCombL1;
    std::vector<float> reverbCombL2;
    std::vector<float> reverbCombR1;
    std::vector<float> reverbCombR2;
    int reverbPosL1 = 0;
    int reverbPosL2 = 0;
    int reverbPosR1 = 0;
    int reverbPosR2 = 0;
};

} // namespace siliconplayer::effects

#endif // SILICONPLAYER_OPENMPT_DSP_EFFECTS_H
