/*
 * OpenMptDspEffects.cpp
 * ---------------------
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

#include "OpenMptDspEffects.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <limits>

namespace {

constexpr float kPi = 3.14159265358979323846f;
constexpr int kNumReverbPresets = 29;
constexpr int kReflectionsCount = 8;
constexpr int kSndmixReflectionsDelayMask = 0x1fff;
constexpr int kSndmixPrediffusionDelayMask = 0x7f;
constexpr int kSndmixReverbDelayMask = 0xfff;
constexpr int kRvbDif1LLen = 149 * 2;
constexpr int kRvbDif1RLen = 223 * 2;
constexpr int kRvbDif2LLen = 421 * 2;
constexpr int kRvbDif2RLen = 647 * 2;
constexpr int kRvbDly1LLen = 683 * 2;
constexpr int kRvbDly1RLen = 811 * 2;
constexpr int kRvbDly2LLen = 773 * 2;
constexpr int kRvbDly2RLen = 1013 * 2;
constexpr int kRvbDlyMask = 2047;
constexpr int kRvbMinRefDelay = 96;
constexpr int kRvbMaxRefDelay = 7500;
constexpr int kRvbMinRvbDelay = 128;
constexpr int kRvbMaxRvbDelay = 3800;
constexpr int kTankLengthAvg = (kRvbDif1LLen + kRvbDif1RLen + kRvbDif2LLen + kRvbDif2RLen + kRvbDly1LLen + kRvbDly1RLen + kRvbDly2LLen + kRvbDly2RLen) / 2;
constexpr int kDcrAmount = 9;
constexpr float kMixScale = static_cast<float>(1 << 24);
constexpr float kGlobalReverbSendGain = 1.0f;

float clampSample(float sample) {
    return std::clamp(sample, -1.0f, 1.0f);
}

template <typename T>
T saturateRound(double value) {
    const double minV = static_cast<double>(std::numeric_limits<T>::lowest());
    const double maxV = static_cast<double>(std::numeric_limits<T>::max());
    const double clamped = std::clamp(value, minV, maxV);
    return static_cast<T>(std::llround(clamped));
}

float sgn(float x) {
    return (x >= 0.0f) ? 1.0f : -1.0f;
}

struct ReverbPresetProperties {
    int32_t room;
    int32_t roomHF;
    float decayTime;
    float decayHFRatio;
    int32_t reflections;
    float reflectionsDelay;
    int32_t reverb;
    float reverbDelay;
    float diffusion;
    float density;
};

struct ReflectionPreset {
    int32_t delayFactor;
    int16_t gainLL;
    int16_t gainRR;
    int16_t gainLR;
    int16_t gainRL;
};

struct EnvironmentReflection {
    int16_t gainLL;
    int16_t gainRR;
    int16_t gainLR;
    int16_t gainRL;
    uint32_t delay;
};

struct EnvironmentReverb {
    int32_t reverbLevel;
    int32_t reflectionsLevel;
    int32_t roomHF;
    uint32_t reverbDecay;
    int32_t preDiffusion;
    int32_t tankDiffusion;
    uint32_t reverbDelay;
    float reverbDamping;
    int32_t reverbDecaySamples;
    std::array<EnvironmentReflection, 8> reflections {};
};

constexpr std::array<ReverbPresetProperties, kNumReverbPresets> kReverbPresets = {{
        { -1000, -200, 1.30f, 0.90f, 0, 0.002f, 0, 0.010f, 100.0f, 75.0f },
        { -1000, -600, 1.10f, 0.83f, -400, 0.005f, 500, 0.010f, 100.0f, 100.0f },
        { -1000, -600, 1.30f, 0.83f, -1000, 0.010f, -200, 0.020f, 100.0f, 100.0f },
        { -1000, -600, 1.50f, 0.83f, -1600, 0.020f, -1000, 0.040f, 100.0f, 100.0f },
        { -1000, -600, 1.80f, 0.70f, -1300, 0.015f, -800, 0.030f, 100.0f, 100.0f },
        { -1000, -600, 1.80f, 0.70f, -2000, 0.030f, -1400, 0.060f, 100.0f, 100.0f },
        { -1000, -100, 1.49f, 0.83f, -2602, 0.007f, 200, 0.011f, 100.0f, 100.0f },
        { -1000, -6000, 0.17f, 0.10f, -1204, 0.001f, 207, 0.002f, 100.0f, 100.0f },
        { -1000, -454, 0.40f, 0.83f, -1646, 0.002f, 53, 0.003f, 100.0f, 100.0f },
        { -1000, -1200, 1.49f, 0.54f, -370, 0.007f, 1030, 0.011f, 100.0f, 60.0f },
        { -1000, -6000, 0.50f, 0.10f, -1376, 0.003f, -1104, 0.004f, 100.0f, 100.0f },
        { -1000, -300, 2.31f, 0.64f, -711, 0.012f, 83, 0.017f, 100.0f, 100.0f },
        { -1000, -476, 4.32f, 0.59f, -789, 0.020f, -289, 0.030f, 100.0f, 100.0f },
        { -1000, -500, 3.92f, 0.70f, -1230, 0.020f, -2, 0.029f, 100.0f, 100.0f },
        { -1000, 0, 2.91f, 1.30f, -602, 0.015f, -302, 0.022f, 100.0f, 100.0f },
        { -1000, -698, 7.24f, 0.33f, -1166, 0.020f, 16, 0.030f, 100.0f, 100.0f },
        { -1000, -1000, 10.05f, 0.23f, -602, 0.020f, 198, 0.030f, 100.0f, 100.0f },
        { -1000, -4000, 0.30f, 0.10f, -1831, 0.002f, -1630, 0.030f, 100.0f, 100.0f },
        { -1000, -300, 1.49f, 0.59f, -1219, 0.007f, 441, 0.011f, 100.0f, 100.0f },
        { -1000, -237, 2.70f, 0.79f, -1214, 0.013f, 395, 0.020f, 100.0f, 100.0f },
        { -1000, -270, 1.49f, 0.86f, -1204, 0.007f, -4, 0.011f, 100.0f, 100.0f },
        { -1000, -3300, 1.49f, 0.54f, -2560, 0.162f, -613, 0.088f, 79.0f, 100.0f },
        { -1000, -800, 1.49f, 0.67f, -2273, 0.007f, -2217, 0.011f, 50.0f, 100.0f },
        { -1000, -2500, 1.49f, 0.21f, -2780, 0.300f, -2014, 0.100f, 27.0f, 100.0f },
        { -1000, -1000, 1.49f, 0.83f, -10000, 0.061f, 500, 0.025f, 100.0f, 100.0f },
        { -1000, -2000, 1.49f, 0.50f, -2466, 0.179f, -2514, 0.100f, 21.0f, 100.0f },
        { -1000, 0, 1.65f, 1.50f, -1363, 0.008f, -1153, 0.012f, 100.0f, 100.0f },
        { -1000, -1000, 2.81f, 0.14f, 429, 0.014f, 648, 0.021f, 80.0f, 60.0f },
        { -1000, -4000, 1.49f, 0.10f, -449, 0.007f, 1700, 0.011f, 100.0f, 100.0f },
}};

constexpr std::array<ReflectionPreset, kReflectionsCount> kReflectionPresets = {{
        {0, 9830, 6554, 0, 0},
        {10, 6554, 13107, 0, 0},
        {24, -9830, 13107, 0, 0},
        {36, 13107, -6554, 0, 0},
        {54, 16384, 16384, -1638, -1638},
        {61, -13107, 8192, -328, -328},
        {73, -11468, -11468, -3277, 3277},
        {87, 13107, -9830, 4916, -4916},
}};

double mBToLinear64(int32_t valueMb) {
    if (!valueMb) return 1.0;
    if (valueMb <= -100000) return 0.0;
    const double val = valueMb * 3.321928094887362304 / (100.0 * 20.0);
    return std::pow(2.0, val - static_cast<int32_t>(0.5 + val));
}

void i3dl2ToGeneric(const ReverbPresetProperties& preset, EnvironmentReverb& rvb, float outputFreq) {
    rvb.reverbLevel = preset.reverb;
    rvb.reflectionsLevel = preset.reflections;
    rvb.roomHF = preset.roomHF;

    int32_t maxLevel = (rvb.reverbLevel > rvb.reflectionsLevel) ? rvb.reverbLevel : rvb.reflectionsLevel;
    if (maxLevel < -600) {
        maxLevel += 600;
        rvb.reverbLevel -= maxLevel;
        rvb.reflectionsLevel -= maxLevel;
    }

    const int32_t density = 8192 + static_cast<int32_t>(79.31f * preset.density);
    rvb.preDiffusion = density;
    int32_t tailDiffusion = static_cast<int32_t>((0.15f + preset.diffusion * (0.36f * 0.01f)) * 32767.0f);
    if (tailDiffusion > 0x7f00) tailDiffusion = 0x7f00;
    rvb.tankDiffusion = tailDiffusion;

    float reflectionsDelay = preset.reflectionsDelay;
    if (reflectionsDelay > 0.100f) reflectionsDelay = 0.100f;
    int32_t reverbDelay = static_cast<int32_t>(preset.reverbDelay * outputFreq);
    int32_t reflectionsDelaySamples = static_cast<int32_t>(reflectionsDelay * outputFreq);
    int32_t reverbDecayTime = static_cast<int32_t>(preset.decayTime * outputFreq);
    if (reflectionsDelaySamples < kRvbMinRefDelay) {
        reverbDelay -= (kRvbMinRefDelay - reflectionsDelaySamples);
        reflectionsDelaySamples = kRvbMinRefDelay;
    }
    if (reflectionsDelaySamples > kRvbMaxRefDelay) {
        reverbDelay += (reflectionsDelaySamples - kRvbMaxRefDelay);
        reflectionsDelaySamples = kRvbMaxRefDelay;
    }
    if (reverbDelay < kRvbMinRvbDelay) {
        reverbDecayTime -= (kRvbMinRvbDelay - reverbDelay);
        reverbDelay = kRvbMinRvbDelay;
    }
    if (reverbDelay > kRvbMaxRvbDelay) {
        reverbDecayTime += (reverbDelay - kRvbMaxRvbDelay);
        reverbDelay = kRvbMaxRvbDelay;
    }
    rvb.reverbDelay = static_cast<uint32_t>(reverbDelay);
    rvb.reverbDecaySamples = reverbDecayTime;

    for (int i = 0; i < kReflectionsCount; ++i) {
        rvb.reflections[static_cast<size_t>(i)].delay = static_cast<uint32_t>(
                reflectionsDelaySamples + ((kReflectionPresets[static_cast<size_t>(i)].delayFactor * reverbDelay + 50) / 100)
        );
        rvb.reflections[static_cast<size_t>(i)].gainLL = kReflectionPresets[static_cast<size_t>(i)].gainLL;
        rvb.reflections[static_cast<size_t>(i)].gainRL = kReflectionPresets[static_cast<size_t>(i)].gainRL;
        rvb.reflections[static_cast<size_t>(i)].gainLR = kReflectionPresets[static_cast<size_t>(i)].gainLR;
        rvb.reflections[static_cast<size_t>(i)].gainRR = kReflectionPresets[static_cast<size_t>(i)].gainRR;
    }

    if (kTankLengthAvg < 10) return;
    const float delayFactor = (reverbDecayTime <= kTankLengthAvg) ? 1.0f : (static_cast<float>(kTankLengthAvg) / static_cast<float>(reverbDecayTime));
    rvb.reverbDecay = static_cast<uint32_t>(std::pow(0.001f, delayFactor) * 32768.0f);

    const float decayTimeHF = static_cast<float>(reverbDecayTime) * preset.decayHFRatio;
    const float delayFactorHF = (decayTimeHF <= static_cast<float>(kTankLengthAvg))
                                ? 1.0f
                                : (static_cast<float>(kTankLengthAvg) / decayTimeHF);
    rvb.reverbDamping = std::pow(0.001f, delayFactorHF);
}

int32_t onePoleLowPassCoef(int32_t scale, double g, double fc, double fs) {
    if (g > 0.999999) return 0;
    g *= g;
    const double scaleOver1Mg = scale / (1.0 - g);
    const double cosw = std::cos((2.0 * kPi) * fc / fs);
    return saturateRound<int32_t>((1.0 - (std::sqrt((g + g) * (1.0 - cosw) - g * g * (1.0 - cosw * cosw)) + g * cosw)) * scaleOver1Mg);
}

int16_t clamp16(int32_t x) {
    return static_cast<int16_t>(std::clamp(x, static_cast<int32_t>(std::numeric_limits<int16_t>::min()), static_cast<int32_t>(std::numeric_limits<int16_t>::max())));
}

int16_t readStereo(const std::vector<int16_t>& buffer, int pos, bool right) {
    return buffer[static_cast<size_t>(pos) * 2 + (right ? 1 : 0)];
}

void writeStereo(std::vector<int16_t>& buffer, int pos, int16_t l, int16_t r) {
    const size_t idx = static_cast<size_t>(pos) * 2;
    buffer[idx] = l;
    buffer[idx + 1] = r;
}

} // namespace

namespace siliconplayer::effects {

void OpenMptDspEffects::shelfEq(
        int32_t scale,
        int32_t& outA1,
        int32_t& outB0,
        int32_t& outB1,
        int32_t fc,
        int32_t fs,
        float gainDC,
        float gainFT,
        float gainPI) {
    float a1;
    float b0;
    float b1;
    const float wT = kPi * static_cast<float>(fc) / static_cast<float>(fs);
    const float gainPI2 = gainPI * gainPI;
    const float gainFT2 = gainFT * gainFT;
    const float gainDC2 = gainDC * gainDC;
    float quad = gainPI2 + gainDC2 - (gainFT2 * 2.0f);

    float alpha = 0.0f;
    if (quad != 0.0f) {
        const float lambda = (gainPI2 - gainDC2) / quad;
        alpha = lambda - (sgn(lambda) * std::sqrt(std::max((lambda * lambda) - 1.0f, 0.0f)));
    }

    const float beta0 = 0.5f * ((gainDC + gainPI) + (gainDC - gainPI) * alpha);
    const float beta1 = 0.5f * ((gainDC - gainPI) + (gainDC + gainPI) * alpha);
    const float rho = std::sin((wT * 0.5f) - (kPi / 4.0f)) / std::sin((wT * 0.5f) + (kPi / 4.0f));

    quad = 1.0f / (1.0f + rho * alpha);
    b0 = ((beta0 + rho * beta1) * quad);
    b1 = ((beta1 + rho * beta0) * quad);
    a1 = -((rho + alpha) * quad);

    outA1 = saturateRound<int32_t>(a1 * static_cast<float>(scale));
    outB0 = saturateRound<int32_t>(b0 * static_cast<float>(scale));
    outB1 = saturateRound<int32_t>(b1 * static_cast<float>(scale));
}

void OpenMptDspEffects::reset() {
    configuredSampleRate = 0;
    bassX1 = 0;
    bassY1 = 0;
    bassDcrY1L = 0;
    bassDcrX1L = 0;
    bassDcrY1R = 0;
    bassDcrX1R = 0;
    surroundDelay.clear();
    surroundWritePos = 0;
    reverbConfiguredPreset = -1;
    reverbConfiguredDepth = -1;
    reverbInputY1L = 0;
    reverbInputY1R = 0;
    reverbDcrX1L = 0;
    reverbDcrX1R = 0;
    reverbDcrY1L = 0;
    reverbDcrY1R = 0;
    reverbRefMasterGain = 0;
    reverbLateMasterGain = 0;
    reverbReflectionsGain = 0;
    reverbPreDifPos = 0;
    reverbDelayPos = 0;
    reverbRefOutPos = 0;
    reverbLateDelay = 0;
    reverbLateDelayPos = 0;
    reverbRoomLpCoeffL = 0;
    reverbRoomLpCoeffR = 0;
    reverbRoomHistoryL = 0;
    reverbRoomHistoryR = 0;
    reverbPreDifCoeffL = 0;
    reverbPreDifCoeffR = 0;
    reverbDifCoeffL = 0;
    reverbDifCoeffR = 0;
    reverbDecayDcL = 0;
    reverbDecayDcR = 0;
    reverbDecayLpL = 0;
    reverbDecayLpR = 0;
    reverbLpHist0L = 0;
    reverbLpHist0R = 0;
    reverbLpHist1L = 0;
    reverbLpHist1R = 0;
    reverbOutGain0L = 0;
    reverbOutGain0R = 0;
    reverbOutGain1L = 0;
    reverbOutGain1R = 0;
    for (auto& ref : reverbReflections) {
        ref = {};
    }
    reverbRefDelayBuffer.clear();
    reverbPreDifBuffer.clear();
    reverbRefOutBuffer.clear();
    reverbDiffusion1.clear();
    reverbDiffusion2.clear();
    reverbDelay1.clear();
    reverbDelay2.clear();
    reverbWetWork.clear();
    reverbDryWork.clear();
}

void OpenMptDspEffects::resetForSampleRate(int sampleRate) {
    const int safeRate = std::max(8000, sampleRate);
    if (safeRate == configuredSampleRate) {
        return;
    }

    configuredSampleRate = safeRate;
    bassX1 = 0;
    bassY1 = 0;
    bassDcrY1L = 0;
    bassDcrX1L = 0;
    bassDcrY1R = 0;
    bassDcrX1R = 0;

    const int maxSurroundDelayFrames = std::max(16, (safeRate * 45) / 1000);
    surroundDelay.assign(static_cast<size_t>(maxSurroundDelayFrames), 0);
    surroundWritePos = 0;
    surroundConfiguredDelayMs = 20;
    surroundConfiguredDepth = 8;
    surroundHpX1 = 0;
    surroundHpY1 = 0;
    surroundLpY1 = 0;
    shelfEq(1024, surroundHpA1, surroundHpB0, surroundHpB1, 200, safeRate, 0.0f, 0.5f, 1.0f);
    shelfEq(1024, surroundLpA1, surroundLpB0, surroundLpB1, 7000, safeRate, 1.0f, 0.75f, 0.0f);
    surroundHpB0 = (surroundHpB0 * surroundConfiguredDepth) >> 5;
    surroundHpB1 = (surroundHpB1 * surroundConfiguredDepth) >> 5;
    surroundLpB0 *= 2;
    surroundLpB1 *= 2;
    reverbConfiguredPreset = -1;
    reverbConfiguredDepth = -1;
    reverbInputY1L = 0;
    reverbInputY1R = 0;
    reverbDcrX1L = 0;
    reverbDcrX1R = 0;
    reverbDcrY1L = 0;
    reverbDcrY1R = 0;
    reverbPreDifPos = 0;
    reverbDelayPos = 0;
    reverbRefOutPos = 0;
    reverbLateDelayPos = 0;
    reverbRoomHistoryL = 0;
    reverbRoomHistoryR = 0;
    reverbLpHist0L = 0;
    reverbLpHist0R = 0;
    reverbLpHist1L = 0;
    reverbLpHist1R = 0;
    reverbRefDelayBuffer.assign(static_cast<size_t>(kSndmixReflectionsDelayMask + 1) * 2, 0);
    reverbPreDifBuffer.assign(static_cast<size_t>(kSndmixPrediffusionDelayMask + 1) * 2, 0);
    reverbRefOutBuffer.assign(static_cast<size_t>(kSndmixReverbDelayMask + 1) * 2, 0);
    reverbDiffusion1.assign(static_cast<size_t>(kRvbDlyMask + 1) * 2, 0);
    reverbDiffusion2.assign(static_cast<size_t>(kRvbDlyMask + 1) * 2, 0);
    reverbDelay1.assign(static_cast<size_t>(kRvbDlyMask + 1) * 2, 0);
    reverbDelay2.assign(static_cast<size_t>(kRvbDlyMask + 1) * 2, 0);
}

void OpenMptDspEffects::process(
        float* interleavedBuffer,
        int frames,
        int channels,
        int sampleRate,
        const OpenMptDspParams& params) {
    if (!interleavedBuffer || frames <= 0 || channels <= 0) {
        return;
    }

    resetForSampleRate(sampleRate);

    if (params.bassEnabled) {
        applyBass(interleavedBuffer, frames, channels, sampleRate, params);
    }
    if (params.surroundEnabled) {
        applySurround(interleavedBuffer, frames, channels, sampleRate, params);
    }
    if (params.reverbEnabled) {
        applyReverb(interleavedBuffer, frames, channels, sampleRate, params);
    }
    if (params.bitCrushEnabled) {
        applyBitCrush(interleavedBuffer, frames, channels, params);
    }
}

void OpenMptDspEffects::applyBass(
        float* buffer,
        int frames,
        int channels,
        int sampleRate,
        const OpenMptDspParams& params) {
    const int depthStep = std::clamp(params.bassDepth, 0, 4);
    const int rangeStep = std::clamp(params.bassRange, 0, 4);
    const int nXBassRange = std::clamp((4 - rangeStep) * 5 + 1, 5, 21);
    const int nXBassCutOff = std::clamp(50 + (nXBassRange + 2) * 20, 60, 600);
    int nXBassGain = std::clamp(8 - depthStep, 4, 8);
    int32_t a1 = 0;
    int32_t b0 = 1024;
    int32_t b1 = 0;
    shelfEq(
            1024,
            a1,
            b0,
            b1,
            nXBassCutOff,
            std::max(sampleRate, 1),
            1.0f + (1.0f / 16.0f) * static_cast<float>(0x300 >> nXBassGain),
            1.0f,
            0.0000001f
    );
    if (nXBassGain > 5) {
        b0 >>= (nXBassGain - 5);
        b1 >>= (nXBassGain - 5);
    }

    if (channels >= 2) {
        int32_t y1l = bassDcrY1L;
        int32_t x1l = bassDcrX1L;
        int32_t y1r = bassDcrY1R;
        int32_t x1r = bassDcrX1R;
        for (int frame = 0; frame < frames; ++frame) {
            const int idx = frame * channels;
            int32_t inL = static_cast<int32_t>(std::lrint(std::clamp(buffer[idx], -1.0f, 1.0f) * 32767.0f));
            int32_t inR = static_cast<int32_t>(std::lrint(std::clamp(buffer[idx + 1], -1.0f, 1.0f) * 32767.0f));
            const int32_t diffL = x1l - inL;
            const int32_t diffR = x1r - inR;
            x1l = inL;
            x1r = inR;
            inL = diffL / (1 << (kDcrAmount + 1)) - diffL + y1l;
            inR = diffR / (1 << (kDcrAmount + 1)) - diffR + y1r;
            y1l = inL - inL / (1 << kDcrAmount);
            y1r = inR - inR / (1 << kDcrAmount);

            const int32_t x_m = (inL + inR + 0x100) >> 9;
            bassY1 = (b0 * x_m + b1 * bassX1 + a1 * bassY1) >> 2;
            bassX1 = x_m;
            inL += bassY1;
            inR += bassY1;
            bassY1 = (bassY1 + 0x80) >> 8;
            buffer[idx] = clampSample(static_cast<float>(inL) / 32767.0f);
            buffer[idx + 1] = clampSample(static_cast<float>(inR) / 32767.0f);
        }
        bassDcrY1L = y1l;
        bassDcrX1L = x1l;
        bassDcrY1R = y1r;
        bassDcrX1R = x1r;
    } else {
        int32_t y1 = bassDcrY1L;
        int32_t x1 = bassDcrX1L;
        for (int frame = 0; frame < frames; ++frame) {
            int32_t inM = static_cast<int32_t>(std::lrint(std::clamp(buffer[frame], -1.0f, 1.0f) * 32767.0f));
            const int32_t diff = x1 - inM;
            x1 = inM;
            inM = diff / (1 << (kDcrAmount + 1)) - diff + y1;
            y1 = inM - inM / (1 << kDcrAmount);

            const int32_t x_m = (inM + 0x80) >> 8;
            bassY1 = (b0 * x_m + b1 * bassX1 + a1 * bassY1) >> 2;
            bassX1 = x_m;
            inM += bassY1;
            bassY1 = (bassY1 + 0x40) >> 8;
            buffer[frame] = clampSample(static_cast<float>(inM) / 32767.0f);
        }
        bassDcrY1L = y1;
        bassDcrX1L = x1;
    }
}

void OpenMptDspEffects::applySurround(
        float* buffer,
        int frames,
        int channels,
        int sampleRate,
        const OpenMptDspParams& params) {
    if (channels < 2 || surroundDelay.empty()) {
        return;
    }

    const int delayClamped = std::clamp(params.surroundDelayMs, 5, 45);
    const int delayMs = 5 + ((((delayClamped - 5) + 2) / 5) * 5);
    const int depth = std::clamp(params.surroundDepth, 1, 16);
    if (delayMs != surroundConfiguredDelayMs || depth != surroundConfiguredDepth) {
        const int safeRate = std::max(sampleRate, 8000);
        std::fill(surroundDelay.begin(), surroundDelay.end(), 0);
        surroundWritePos = 0;
        surroundConfiguredDelayMs = delayMs;
        surroundConfiguredDepth = depth;
        surroundHpX1 = 0;
        surroundHpY1 = 0;
        surroundLpY1 = 0;
        shelfEq(1024, surroundHpA1, surroundHpB0, surroundHpB1, 200, safeRate, 0.0f, 0.5f, 1.0f);
        shelfEq(1024, surroundLpA1, surroundLpB0, surroundLpB1, 7000, safeRate, 1.0f, 0.75f, 0.0f);
        surroundHpB0 = (surroundHpB0 * depth) >> 5;
        surroundHpB1 = (surroundHpB1 * depth) >> 5;
        surroundLpB0 *= 2;
        surroundLpB1 *= 2;
    }

    for (int frame = 0; frame < frames; ++frame) {
        const int idx = frame * channels;
        const int32_t inL = static_cast<int32_t>(std::lrint(std::clamp(buffer[idx], -1.0f, 1.0f) * kMixScale));
        const int32_t inR = static_cast<int32_t>(std::lrint(std::clamp(buffer[idx + 1], -1.0f, 1.0f) * kMixScale));

        const int32_t secho = surroundDelay[static_cast<size_t>(surroundWritePos)];
        surroundDelay[static_cast<size_t>(surroundWritePos)] = (inL + inR + 256) >> 9;

        const int64_t hpAcc = static_cast<int64_t>(surroundHpB0) * secho
                              + static_cast<int64_t>(surroundHpB1) * surroundHpX1
                              + static_cast<int64_t>(surroundHpA1) * surroundHpY1;
        int32_t v0 = static_cast<int32_t>(hpAcc >> 10);
        surroundHpX1 = secho;
        const int64_t lpAcc = static_cast<int64_t>(surroundLpB0) * v0
                              + static_cast<int64_t>(surroundLpB1) * surroundHpY1
                              + static_cast<int64_t>(surroundLpA1) * surroundLpY1;
        int32_t v = static_cast<int32_t>(lpAcc >> 2);
        surroundHpY1 = v0;
        surroundLpY1 = v >> 8;

        const int32_t outL = inL + v;
        const int32_t outR = inR - v;
        buffer[idx] = clampSample(static_cast<float>(outL) / kMixScale);
        buffer[idx + 1] = clampSample(static_cast<float>(outR) / kMixScale);

        surroundWritePos++;
        if (surroundWritePos >= std::clamp((sampleRate * delayMs) / 1000, 1, static_cast<int>(surroundDelay.size()) - 1)) {
            surroundWritePos = 0;
        }
    }
}

void OpenMptDspEffects::applyReverb(
        float* buffer,
        int frames,
        int channels,
        int sampleRate,
        const OpenMptDspParams& params) {
    if (channels < 2 || reverbRefDelayBuffer.empty() || reverbDelay2.empty() || frames <= 0) {
        return;
    }

    configureReverb(sampleRate, params.reverbPreset);

    const int depth = std::clamp(params.reverbDepth, 1, 16);
    if (depth != reverbConfiguredDepth) {
        int32_t masterGain = (reverbRefMasterGain * depth) >> 4;
        if (masterGain > 0x7fff) masterGain = 0x7fff;
        reverbReflectionsGain = masterGain;

        masterGain = (reverbLateMasterGain * depth) >> 4;
        if (masterGain > 0x10000) masterGain = 0x10000;
        reverbOutGain0L = static_cast<int16_t>((masterGain + 0x7f) >> 3);
        reverbOutGain0R = static_cast<int16_t>((masterGain + 0xff) >> 4);
        reverbOutGain1L = static_cast<int16_t>((masterGain + 0xff) >> 4);
        reverbOutGain1R = static_cast<int16_t>((masterGain + 0x7f) >> 3);
        reverbConfiguredDepth = depth;
    }

    reverbWetWork.resize(static_cast<size_t>(frames) * 2);
    reverbDryWork.resize(static_cast<size_t>(frames) * 2);
    for (int frame = 0; frame < frames; ++frame) {
        const int idx = frame * channels;
        const int32_t inL = static_cast<int32_t>(std::lrint(std::clamp(buffer[idx], -1.0f, 1.0f) * kMixScale));
        const int32_t inR = static_cast<int32_t>(std::lrint(std::clamp(buffer[idx + 1], -1.0f, 1.0f) * kMixScale));
        // Route signal through a dedicated reverb send and rebuild dry mix from send.
        reverbWetWork[static_cast<size_t>(frame) * 2] = static_cast<int32_t>(std::lrint(static_cast<float>(inL) * kGlobalReverbSendGain));
        reverbWetWork[static_cast<size_t>(frame) * 2 + 1] = static_cast<int32_t>(std::lrint(static_cast<float>(inR) * kGlobalReverbSendGain));
        reverbDryWork[static_cast<size_t>(frame) * 2] = 0;
        reverbDryWork[static_cast<size_t>(frame) * 2 + 1] = 0;
    }

    int32_t maxRvbGain = (reverbRefMasterGain > reverbLateMasterGain) ? reverbRefMasterGain : reverbLateMasterGain;
    if (maxRvbGain > 32768) maxRvbGain = 32768;
    int32_t dryVol = (36 - depth) >> 1;
    if (dryVol < 8) dryVol = 8;
    if (dryVol > 16) dryVol = 16;
    dryVol = 16 - (((16 - dryVol) * maxRvbGain) >> 15);
    applyReverbDryMix(reverbDryWork.data(), reverbWetWork.data(), dryVol, frames);

    for (int i = 0; i < frames; ++i) {
        const int pos = i * 2;
        const int32_t xL = reverbWetWork[static_cast<size_t>(pos)] >> 12;
        const int32_t xR = reverbWetWork[static_cast<size_t>(pos + 1)] >> 12;
        reverbInputY1L = xL + (((xL - reverbInputY1L) * reverbRoomLpCoeffL) >> 15);
        reverbInputY1R = xR + (((xR - reverbInputY1R) * reverbRoomLpCoeffR) >> 15);
        reverbWetWork[static_cast<size_t>(pos)] = reverbInputY1L;
        reverbWetWork[static_cast<size_t>(pos + 1)] = reverbInputY1R;
    }

    processReverbPreDelay(reverbWetWork.data(), frames);
    processReverbReflections(reverbWetWork.data(), frames);
    processReverbLate(reverbWetWork.data(), frames);
    processReverbPost(reverbWetWork.data(), reverbDryWork.data(), frames);

    for (int frame = 0; frame < frames; ++frame) {
        const int idx = frame * channels;
        const float outL = static_cast<float>(reverbDryWork[static_cast<size_t>(frame) * 2] / kMixScale);
        const float outR = static_cast<float>(reverbDryWork[static_cast<size_t>(frame) * 2 + 1] / kMixScale);
        buffer[idx] = clampSample(outL);
        buffer[idx + 1] = clampSample(outR);
    }
}

void OpenMptDspEffects::configureReverb(int sampleRate, int preset) {
    const int clampedPreset = std::clamp(preset, 0, kNumReverbPresets - 1);
    if (reverbConfiguredPreset == clampedPreset) {
        return;
    }

    EnvironmentReverb rvb {};
    i3dl2ToGeneric(kReverbPresets[static_cast<size_t>(clampedPreset)], rvb, static_cast<float>(std::max(8000, sampleRate)));

    const int32_t roomLp = onePoleLowPassCoef(32768, mBToLinear64(rvb.roomHF), 5000.0, static_cast<double>(std::max(8000, sampleRate)));
    reverbRoomLpCoeffL = clamp16(roomLp);
    reverbRoomLpCoeffR = clamp16(roomLp);
    reverbPreDifCoeffL = clamp16(rvb.preDiffusion * 2);
    reverbPreDifCoeffR = clamp16(rvb.preDiffusion * 2);

    for (int i = 0; i < kReflectionsCount; ++i) {
        reverbReflections[static_cast<size_t>(i)].delay = rvb.reflections[static_cast<size_t>(i)].delay;
        reverbReflections[static_cast<size_t>(i)].gainLL = rvb.reflections[static_cast<size_t>(i)].gainLL;
        reverbReflections[static_cast<size_t>(i)].gainRL = rvb.reflections[static_cast<size_t>(i)].gainRL;
        reverbReflections[static_cast<size_t>(i)].gainLR = rvb.reflections[static_cast<size_t>(i)].gainLR;
        reverbReflections[static_cast<size_t>(i)].gainRR = rvb.reflections[static_cast<size_t>(i)].gainRR;
    }

    reverbLateDelay = rvb.reverbDelay;
    reverbRefMasterGain = (rvb.reflectionsLevel > -9000) ? saturateRound<int32_t>(mBToLinear64(rvb.reflectionsLevel) * 32768.0) : 0;
    reverbLateMasterGain = (rvb.reverbLevel > -9000) ? saturateRound<int32_t>(mBToLinear64(rvb.reverbLevel) * 32768.0) : 0;

    int32_t tailDiffusion = rvb.tankDiffusion;
    if (tailDiffusion > 0x7f00) tailDiffusion = 0x7f00;
    reverbDifCoeffL = clamp16(tailDiffusion);
    reverbDifCoeffR = clamp16(tailDiffusion);

    int32_t decay = static_cast<int32_t>(rvb.reverbDecay);
    decay = std::clamp(decay, 0, 0x7ff0);
    reverbDecayDcL = clamp16(decay);
    reverbDecayDcR = clamp16(decay);

    const float damping = rvb.reverbDamping * rvb.reverbDamping;
    int32_t dampingLp = onePoleLowPassCoef(32768, static_cast<double>(damping), 5000.0, static_cast<double>(std::max(8000, sampleRate)));
    dampingLp = std::clamp(dampingLp, 0x100, 0x7f00);
    reverbDecayLpL = clamp16(dampingLp);
    reverbDecayLpR = clamp16(dampingLp);

    reverbConfiguredPreset = clampedPreset;
    reverbConfiguredDepth = -1;
}

void OpenMptDspEffects::processReverbPreDelay(const int32_t* in, int32_t frames) {
    uint32_t preDifPos = reverbPreDifPos;
    uint32_t delayPos = reverbDelayPos - 1;
    int16_t historyL = reverbRoomHistoryL;
    int16_t historyR = reverbRoomHistoryR;

    for (int32_t i = 0; i < frames; ++i) {
        const int32_t inL = clamp16(in[static_cast<size_t>(i) * 2]);
        const int32_t inR = clamp16(in[static_cast<size_t>(i) * 2 + 1]);
        const int32_t lpL = (clamp16(static_cast<int32_t>(historyL) - inL) * reverbRoomLpCoeffL) / 65536;
        const int32_t lpR = (clamp16(static_cast<int32_t>(historyR) - inR) * reverbRoomLpCoeffR) / 65536;
        historyL = clamp16(clamp16(lpL + lpL) + inL);
        historyR = clamp16(clamp16(lpR + lpR) + inR);

        const int32_t preDifL = readStereo(reverbPreDifBuffer, static_cast<int>(preDifPos), false);
        const int32_t preDifR = readStereo(reverbPreDifBuffer, static_cast<int>(preDifPos), true);
        preDifPos = (preDifPos + 1) & kSndmixPrediffusionDelayMask;
        delayPos = (delayPos + 1) & kSndmixReflectionsDelayMask;

        const int16_t preDif2L = clamp16(static_cast<int32_t>(historyL) - (preDifL * reverbPreDifCoeffL) / 65536);
        const int16_t preDif2R = clamp16(static_cast<int32_t>(historyR) - (preDifR * reverbPreDifCoeffR) / 65536);
        writeStereo(reverbPreDifBuffer, static_cast<int>(preDifPos), preDif2L, preDif2R);
        writeStereo(
                reverbRefDelayBuffer,
                static_cast<int>(delayPos),
                clamp16((reverbPreDifCoeffL * preDif2L) / 65536 + preDifL),
                clamp16((reverbPreDifCoeffR * preDif2R) / 65536 + preDifR));
    }

    reverbPreDifPos = preDifPos;
    reverbRoomHistoryL = historyL;
    reverbRoomHistoryR = historyR;
}

void OpenMptDspEffects::processReverbReflections(int32_t* out, int32_t frames) {
    int pos[7];
    for (int i = 0; i < 7; ++i) {
        pos[i] = static_cast<int>(reverbDelayPos) - static_cast<int>(reverbReflections[static_cast<size_t>(i)].delay) - 1;
    }
    const int16_t refGain = static_cast<int16_t>(reverbReflectionsGain >> 3);
    const uint32_t refOutStart = reverbRefOutPos;

    for (int32_t frame = 0; frame < frames; ++frame) {
        int32_t refOutL = 0;
        int32_t refOutR = 0;
        for (int i = 0; i < 4; ++i) {
            pos[i] = (pos[i] + 1) & kSndmixReflectionsDelayMask;
            const int16_t refL = readStereo(reverbRefDelayBuffer, pos[i], false);
            const int16_t refR = readStereo(reverbRefDelayBuffer, pos[i], true);
            const auto& refl = reverbReflections[static_cast<size_t>(i)];
            refOutL += refL * refl.gainLL + refR * refl.gainRL;
            refOutR += refL * refl.gainLR + refR * refl.gainRR;
        }
        const int16_t stage1L = clamp16(refOutL / (1 << 15));
        const int16_t stage1R = clamp16(refOutR / (1 << 15));

        refOutL = 0;
        refOutR = 0;
        for (int i = 4; i < 7; ++i) {
            pos[i] = (pos[i] + 1) & kSndmixReflectionsDelayMask;
            const int16_t refL = readStereo(reverbRefDelayBuffer, pos[i], false);
            const int16_t refR = readStereo(reverbRefDelayBuffer, pos[i], true);
            const auto& refl = reverbReflections[static_cast<size_t>(i)];
            refOutL += refL * refl.gainLL + refR * refl.gainRL;
            refOutR += refL * refl.gainLR + refR * refl.gainRR;
        }

        const int16_t refMixL = clamp16(stage1L + refOutL / (1 << 15));
        const int16_t refMixR = clamp16(stage1R + refOutR / (1 << 15));
        const int refOutPos = static_cast<int>((refOutStart + static_cast<uint32_t>(frame)) & kSndmixReverbDelayMask);
        writeStereo(reverbRefOutBuffer, refOutPos, refMixL, refMixR);
        out[static_cast<size_t>(frame) * 2] = refMixL * refGain;
        out[static_cast<size_t>(frame) * 2 + 1] = refMixR * refGain;
    }

    reverbRefOutPos = (reverbRefOutPos + static_cast<uint32_t>(frames)) & kSndmixReverbDelayMask;
    reverbDelayPos = (reverbDelayPos + static_cast<uint32_t>(frames)) & kSndmixReflectionsDelayMask;
}

void OpenMptDspEffects::processReverbLate(int32_t* out, int32_t frames) {
    int delayPos = static_cast<int>(reverbLateDelayPos) & kRvbDlyMask;
    const uint32_t refStart = (reverbRefOutPos - static_cast<uint32_t>(frames)) & kSndmixReverbDelayMask;
    const uint32_t refReadStart = (refStart - reverbLateDelay) & kSndmixReverbDelayMask;

    for (int32_t frame = 0; frame < frames; ++frame) {
        const int refPos = static_cast<int>((refReadStart + static_cast<uint32_t>(frame)) & kSndmixReverbDelayMask);
        const int16_t refInL = readStereo(reverbRefOutBuffer, refPos, false);
        const int16_t refInR = readStereo(reverbRefOutBuffer, refPos, true);

        const int delay2LPos = (delayPos - kRvbDly2LLen) & kRvbDlyMask;
        const int delay2RPos = (delayPos - kRvbDly2RLen) & kRvbDlyMask;
        const int32_t delay2LL = readStereo(reverbDelay2, delay2LPos, false);
        const int32_t delay2LR = readStereo(reverbDelay2, delay2LPos, true);
        const int32_t delay2RL = readStereo(reverbDelay2, delay2RPos, false);
        const int32_t delay2RR = readStereo(reverbDelay2, delay2RPos, true);

        const int32_t diff1L = readStereo(reverbDiffusion1, (delayPos - kRvbDif1LLen) & kRvbDlyMask, false);
        const int32_t diff1R = readStereo(reverbDiffusion1, (delayPos - kRvbDif1RLen) & kRvbDlyMask, true);
        const int32_t diff2L = readStereo(reverbDiffusion2, (delayPos - kRvbDif2LLen) & kRvbDlyMask, false);
        const int32_t diff2R = readStereo(reverbDiffusion2, (delayPos - kRvbDif2RLen) & kRvbDlyMask, true);

        const int32_t lpDecayLL = clamp16(static_cast<int32_t>(reverbLpHist0L) - delay2LL) * reverbDecayLpL / 65536;
        const int32_t lpDecayLR = clamp16(static_cast<int32_t>(reverbLpHist0R) - delay2LR) * reverbDecayLpR / 65536;
        const int32_t lpDecayRL = clamp16(static_cast<int32_t>(reverbLpHist1L) - delay2RL) * reverbDecayLpL / 65536;
        const int32_t lpDecayRR = clamp16(static_cast<int32_t>(reverbLpHist1R) - delay2RR) * reverbDecayLpR / 65536;
        reverbLpHist0L = clamp16(clamp16(lpDecayLL + lpDecayLL) + delay2LL);
        reverbLpHist0R = clamp16(clamp16(lpDecayLR + lpDecayLR) + delay2LR);
        reverbLpHist1L = clamp16(clamp16(lpDecayRL + lpDecayRL) + delay2RL);
        reverbLpHist1R = clamp16(clamp16(lpDecayRR + lpDecayRR) + delay2RR);

        const int32_t histDecayL = clamp16((reverbDecayDcL * reverbLpHist0L) / (1 << 15));
        const int32_t histDecayR = clamp16((reverbDecayDcR * reverbLpHist1R) / (1 << 15));
        const int32_t histDecayInL = clamp16(histDecayL + refInL / 4);
        const int32_t histDecayInR = clamp16(histDecayR + refInR / 4);
        const int32_t histDecayInDiffL = clamp16(histDecayInL - (diff1L * reverbDifCoeffL) / 65536);
        const int32_t histDecayInDiffR = clamp16(histDecayInR - (diff1R * reverbDifCoeffR) / 65536);
        writeStereo(reverbDiffusion1, delayPos, static_cast<int16_t>(histDecayInDiffL), static_cast<int16_t>(histDecayInDiffR));

        const int32_t delay1L = clamp16((reverbDifCoeffL * histDecayInDiffL) / 65536 + diff1L);
        const int32_t delay1R = clamp16((reverbDifCoeffR * histDecayInDiffR) / 65536 + diff1R);
        writeStereo(reverbDelay1, delayPos, static_cast<int16_t>(delay1L), static_cast<int16_t>(delay1R));
        const int32_t histDecayInDelayL = clamp16(histDecayInL + delay1L);
        const int32_t histDecayInDelayR = clamp16(histDecayInR + delay1R);

        const int delay1LPos = (delayPos - kRvbDly1LLen) & kRvbDlyMask;
        const int delay1RPos = (delayPos - kRvbDly1RLen) & kRvbDlyMask;
        const int32_t delay1LL = readStereo(reverbDelay1, delay1LPos, false);
        const int32_t delay1LR = readStereo(reverbDelay1, delay1LPos, true);
        const int32_t delay1RL = readStereo(reverbDelay1, delay1RPos, false);
        const int32_t delay1RR = readStereo(reverbDelay1, delay1RPos, true);

        const int32_t delay1GainsL = clamp16((delay1LL * reverbDif2InGain0L + delay1LR * reverbDif2InGain0R) / (1 << 15));
        const int32_t delay1GainsR = clamp16((delay1RL * reverbDif2InGain1L + delay1RR * reverbDif2InGain1R) / (1 << 15));
        const int32_t histDelay1LL = clamp16(clamp16(histDecayInDelayL + delay1LL) - delay1GainsL);
        const int32_t histDelay1LR = clamp16(clamp16(histDecayInDelayR + delay1LR) - delay1GainsR);
        const int32_t histDelay1RL = clamp16(clamp16(histDecayInDelayL + delay1RL) - delay1GainsL);
        const int32_t histDelay1RR = clamp16(clamp16(histDecayInDelayR + delay1RR) - delay1GainsR);
        const int32_t diff2outL = clamp16(delay1GainsL - (diff2L * reverbDifCoeffL) / 65536);
        const int32_t diff2outR = clamp16(delay1GainsR - (diff2R * reverbDifCoeffR) / 65536);
        const int32_t diff2outCoeffsL = (reverbDifCoeffL * diff2outL) / 65536;
        const int32_t diff2outCoeffsR = (reverbDifCoeffR * diff2outR) / 65536;
        writeStereo(reverbDiffusion2, delayPos, static_cast<int16_t>(diff2outL), static_cast<int16_t>(diff2outR));

        const int32_t delay2outL = clamp16(diff2outCoeffsL + diff2L);
        const int32_t delay2outR = clamp16(diff2outCoeffsR + diff2R);
        writeStereo(reverbDelay2, delayPos, static_cast<int16_t>(delay2outL), static_cast<int16_t>(delay2outR));
        delayPos = (delayPos + 1) & kRvbDlyMask;

        out[static_cast<size_t>(frame) * 2] += clamp16(histDelay1LL + delay2outL) * reverbOutGain0L
                                             + clamp16(histDelay1LR + delay2outR) * reverbOutGain0R;
        out[static_cast<size_t>(frame) * 2 + 1] += clamp16(histDelay1RL + clamp16(diff2outCoeffsL)) * reverbOutGain1L
                                                 + clamp16(histDelay1RR + clamp16(diff2outCoeffsR)) * reverbOutGain1R;
    }
    reverbLateDelayPos = static_cast<uint32_t>(delayPos);
}

void OpenMptDspEffects::processReverbPost(const int32_t* wet, int32_t* dry, int32_t frames) {
    int32_t x1L = reverbDcrX1L;
    int32_t x1R = reverbDcrX1R;
    int32_t y1L = reverbDcrY1L;
    int32_t y1R = reverbDcrY1R;
    int32_t inL = 0;
    int32_t inR = 0;
    for (int32_t i = 0; i < frames; ++i) {
        inL = wet[static_cast<size_t>(i) * 2];
        inR = wet[static_cast<size_t>(i) * 2 + 1];
        int32_t outL = dry[static_cast<size_t>(i) * 2];
        int32_t outR = dry[static_cast<size_t>(i) * 2 + 1];

        x1L -= inL;
        x1R -= inR;
        x1L = x1L / (1 << (kDcrAmount + 1)) - x1L;
        x1R = x1R / (1 << (kDcrAmount + 1)) - x1R;
        y1L += x1L;
        y1R += x1R;
        outL += y1L;
        outR += y1R;
        y1L -= y1L / (1 << kDcrAmount);
        y1R -= y1R / (1 << kDcrAmount);
        x1L = inL;
        x1R = inR;

        dry[static_cast<size_t>(i) * 2] = outL;
        dry[static_cast<size_t>(i) * 2 + 1] = outR;
    }
    reverbDcrX1L = inL;
    reverbDcrX1R = inR;
    reverbDcrY1L = y1L;
    reverbDcrY1R = y1R;
}

void OpenMptDspEffects::applyReverbDryMix(int32_t* dry, const int32_t* wet, int32_t dryVol, int32_t frames) {
    for (int32_t i = 0; i < frames; ++i) {
        dry[static_cast<size_t>(i) * 2] += (wet[static_cast<size_t>(i) * 2] >> 4) * dryVol;
        dry[static_cast<size_t>(i) * 2 + 1] += (wet[static_cast<size_t>(i) * 2 + 1] >> 4) * dryVol;
    }
}

void OpenMptDspEffects::applyBitCrush(float* buffer, int frames, int channels, const OpenMptDspParams& params) {
    const int bits = std::clamp(params.bitCrushBits, 1, 24);
    const int precisionBits = 24;
    const uint32_t mask = ~((uint32_t{1} << (precisionBits - bits)) - 1u);
    const int samples = frames * channels;
    for (int i = 0; i < samples; ++i) {
        const float sample = std::clamp(buffer[i], -1.0f, 1.0f);
        const int32_t fixed = static_cast<int32_t>(std::lrint(sample * static_cast<float>((1 << precisionBits) - 1)));
        const int32_t crushed = static_cast<int32_t>(static_cast<uint32_t>(fixed) & mask);
        buffer[i] = clampSample(static_cast<float>(crushed) / static_cast<float>((1 << precisionBits) - 1));
    }
}

} // namespace siliconplayer::effects
