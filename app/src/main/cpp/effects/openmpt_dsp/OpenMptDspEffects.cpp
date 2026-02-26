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

float clampSample(float sample) {
    return std::clamp(sample, -1.0f, 1.0f);
}

float dbToLinear(float db) {
    return std::pow(10.0f, db / 20.0f);
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

struct ReverbPresetProfile {
    float feedback;
    float damping;
    float stereoCross;
};

ReverbPresetProfile profileForPreset(int preset) {
    const int clamped = std::clamp(preset, 0, kNumReverbPresets - 1);
    const float t = static_cast<float>(clamped) / static_cast<float>(kNumReverbPresets - 1);

    // Wide enough spread to represent room/hall/plate-like variants without
    // a heavy physical model. This keeps CPU usage low for mobile.
    ReverbPresetProfile profile {};
    profile.feedback = 0.58f + (0.30f * t);
    profile.damping = 0.08f + (0.28f * (1.0f - t));
    profile.stereoCross = 0.07f + (0.23f * t);
    return profile;
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
    bassLpState = { 0.0f, 0.0f };
    surroundDelayL.clear();
    surroundDelayR.clear();
    surroundWritePos = 0;
    reverbCombL1.clear();
    reverbCombL2.clear();
    reverbCombR1.clear();
    reverbCombR2.clear();
    reverbPosL1 = 0;
    reverbPosL2 = 0;
    reverbPosR1 = 0;
    reverbPosR2 = 0;
}

void OpenMptDspEffects::resetForSampleRate(int sampleRate) {
    const int safeRate = std::max(8000, sampleRate);
    if (safeRate == configuredSampleRate) {
        return;
    }

    configuredSampleRate = safeRate;
    bassLpState = { 0.0f, 0.0f };

    const int maxSurroundDelayFrames = std::max(16, (safeRate * 45) / 1000);
    surroundDelayL.assign(static_cast<size_t>(maxSurroundDelayFrames), 0.0f);
    surroundDelayR.assign(static_cast<size_t>(maxSurroundDelayFrames), 0.0f);
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

    const auto delayFrames = [safeRate](float ms) {
        return std::max(8, static_cast<int>((safeRate * ms) / 1000.0f));
    };

    reverbCombL1.assign(static_cast<size_t>(delayFrames(29.7f)), 0.0f);
    reverbCombL2.assign(static_cast<size_t>(delayFrames(37.1f)), 0.0f);
    reverbCombR1.assign(static_cast<size_t>(delayFrames(31.3f)), 0.0f);
    reverbCombR2.assign(static_cast<size_t>(delayFrames(41.1f)), 0.0f);
    reverbPosL1 = 0;
    reverbPosL2 = 0;
    reverbPosR1 = 0;
    reverbPosR2 = 0;
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
    const int depth = std::clamp(params.bassDepth, 4, 8);
    const int range = std::clamp(params.bassRange, 5, 21);

    const float cutoffHz = static_cast<float>((range + 2) * 20);
    const float dt = 1.0f / static_cast<float>(std::max(sampleRate, 1));
    const float rc = 1.0f / (2.0f * kPi * std::max(20.0f, cutoffHz));
    const float alpha = std::clamp(dt / (rc + dt), 0.001f, 0.95f);

    const float boostLinear = dbToLinear(3.0f + static_cast<float>(depth - 4) * 1.5f);
    const float wet = std::clamp((boostLinear - 1.0f) * 0.34f, 0.0f, 1.2f);

    for (int frame = 0; frame < frames; ++frame) {
        for (int ch = 0; ch < channels; ++ch) {
            const int idx = frame * channels + ch;
            const int stateIndex = std::min(ch, 1);
            const float input = buffer[idx];
            float lp = bassLpState[static_cast<size_t>(stateIndex)];
            lp += alpha * (input - lp);
            bassLpState[static_cast<size_t>(stateIndex)] = lp;
            buffer[idx] = clampSample(input + (lp * wet));
        }
    }
}

void OpenMptDspEffects::applySurround(
        float* buffer,
        int frames,
        int channels,
        int sampleRate,
        const OpenMptDspParams& params) {
    if (channels < 2 || surroundDelayL.empty()) {
        return;
    }

    const int delayMs = std::clamp(params.surroundDelayMs, 5, 45);
    const int depth = std::clamp(params.surroundDepth, 1, 16);
    if (delayMs != surroundConfiguredDelayMs || depth != surroundConfiguredDepth) {
        const int safeRate = std::max(sampleRate, 8000);
        std::fill(surroundDelayL.begin(), surroundDelayL.end(), 0.0f);
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
        const int32_t inL = static_cast<int32_t>(std::lrint(std::clamp(buffer[idx], -1.0f, 1.0f) * 32767.0f));
        const int32_t inR = static_cast<int32_t>(std::lrint(std::clamp(buffer[idx + 1], -1.0f, 1.0f) * 32767.0f));

        const int32_t secho = static_cast<int32_t>(std::lrint(surroundDelayL[static_cast<size_t>(surroundWritePos)]));
        surroundDelayL[static_cast<size_t>(surroundWritePos)] = static_cast<float>((inL + inR + 256) >> 9);

        int32_t v0 = (surroundHpB0 * secho + surroundHpB1 * surroundHpX1 + surroundHpA1 * surroundHpY1) >> 10;
        surroundHpX1 = secho;
        int32_t v = (surroundLpB0 * v0 + surroundLpB1 * surroundHpY1 + surroundLpA1 * surroundLpY1) >> 2;
        surroundHpY1 = v0;
        surroundLpY1 = v >> 8;

        const int32_t outL = inL + v;
        const int32_t outR = inR - v;
        buffer[idx] = clampSample(static_cast<float>(outL) / 32767.0f);
        buffer[idx + 1] = clampSample(static_cast<float>(outR) / 32767.0f);

        surroundWritePos++;
        if (surroundWritePos >= std::clamp((sampleRate * delayMs) / 1000, 1, static_cast<int>(surroundDelayL.size()) - 1)) {
            surroundWritePos = 0;
        }
    }
}

void OpenMptDspEffects::applyReverb(
        float* buffer,
        int frames,
        int channels,
        int,
        const OpenMptDspParams& params) {
    if (channels < 2 || reverbCombL1.empty() || reverbCombL2.empty() || reverbCombR1.empty() || reverbCombR2.empty()) {
        return;
    }

    const int depthValue = std::clamp(params.reverbDepth, 1, 16);
    const float wet = static_cast<float>(depthValue) / 16.0f;
    const float dry = 1.0f - (wet * 0.45f);

    const ReverbPresetProfile profile = profileForPreset(params.reverbPreset);
    const float feedback = std::clamp(profile.feedback, 0.1f, 0.95f);
    const float damping = std::clamp(profile.damping, 0.0f, 0.95f);
    const float cross = std::clamp(profile.stereoCross, 0.0f, 0.8f);

    for (int frame = 0; frame < frames; ++frame) {
        const int idx = frame * channels;
        const float inputL = buffer[idx];
        const float inputR = buffer[idx + 1];

        const float tapL1 = reverbCombL1[static_cast<size_t>(reverbPosL1)];
        const float tapL2 = reverbCombL2[static_cast<size_t>(reverbPosL2)];
        const float tapR1 = reverbCombR1[static_cast<size_t>(reverbPosR1)];
        const float tapR2 = reverbCombR2[static_cast<size_t>(reverbPosR2)];

        const float wetL = ((tapL1 + tapL2) * 0.5f) + ((tapR1 + tapR2) * 0.5f * cross);
        const float wetR = ((tapR1 + tapR2) * 0.5f) + ((tapL1 + tapL2) * 0.5f * cross);

        const float feedL = (inputL + (wetL * feedback)) * (1.0f - damping);
        const float feedR = (inputR + (wetR * feedback)) * (1.0f - damping);

        reverbCombL1[static_cast<size_t>(reverbPosL1)] = feedL;
        reverbCombL2[static_cast<size_t>(reverbPosL2)] = feedL * 0.93f;
        reverbCombR1[static_cast<size_t>(reverbPosR1)] = feedR;
        reverbCombR2[static_cast<size_t>(reverbPosR2)] = feedR * 0.91f;

        buffer[idx] = clampSample((inputL * dry) + (wetL * wet * 0.6f));
        buffer[idx + 1] = clampSample((inputR * dry) + (wetR * wet * 0.6f));

        reverbPosL1 = (reverbPosL1 + 1) % static_cast<int>(reverbCombL1.size());
        reverbPosL2 = (reverbPosL2 + 1) % static_cast<int>(reverbCombL2.size());
        reverbPosR1 = (reverbPosR1 + 1) % static_cast<int>(reverbCombR1.size());
        reverbPosR2 = (reverbPosR2 + 1) % static_cast<int>(reverbCombR2.size());
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
