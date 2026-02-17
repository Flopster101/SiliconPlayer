#include "ReSidEmu.h"

#include <cmath>
#include <sstream>
#include <string>

#include <resid/siddefs.h>

namespace libsidplayfp {

const char* ReSidEmu::getCredits() {
    static std::string credits;
    if (credits.empty()) {
        std::ostringstream out;
        out << "ReSID Engine:\n";
        out << "\t(C) 1999-2002 Simon White\n";
        out << "MOS6581/CSG8580 Emulation (ReSID V" << resid_version_string << "):\n";
        out << "\t(C) 1999-2010 Dag Lem\n";
        credits = out.str();
    }
    return credits.c_str();
}

ReSidEmu::ReSidEmu(sidbuilder* builder)
        : sidemu(builder),
          sid(*(new reSID::SID)) {
    reset(0);
}

ReSidEmu::~ReSidEmu() {
    delete &sid;
    delete[] m_buffer;
}

void ReSidEmu::bias(double dacBias) {
    sid.adjust_filter_bias(dacBias);
}

void ReSidEmu::reset(uint8_t volume) {
    m_accessClk = 0;
    sid.reset();
    sid.write(0x18, volume);
}

uint8_t ReSidEmu::read(uint_least8_t addr) {
    clock();
    return sid.read(addr);
}

void ReSidEmu::write(uint_least8_t addr, uint8_t data) {
    clock();
    sid.write(addr, data);
}

void ReSidEmu::clock() {
    reSID::cycle_count cycles = eventScheduler->getTime(EVENT_CLOCK_PHI1) - m_accessClk;
    m_accessClk += cycles;
    m_bufferpos += sid.clock(cycles, m_buffer + m_bufferpos, bufferSize - m_bufferpos, 1);
    // Adjust in case not all cycles have been consumed.
    m_accessClk -= cycles;
}

void ReSidEmu::filter(bool enable) {
    sid.enable_filter(enable);
}

bool ReSidEmu::applySamplingConfiguration() {
    reSID::sampling_method sampleMethod = reSID::SAMPLE_INTERPOLATE;
    switch (requestedSampling) {
        case SidConfig::INTERPOLATE:
            // Upstream reSID fast mode is non-cycle-accurate and explicitly hacky for MOS8580.
            // Keep fast path for 6581, but force interpolated for 8580 to avoid filter corruption.
            sampleMethod = (currentModel == SidConfig::MOS8580)
                           ? reSID::SAMPLE_INTERPOLATE
                           : reSID::SAMPLE_FAST;
            break;
        case SidConfig::RESAMPLE_INTERPOLATE:
            sampleMethod = reSID::SAMPLE_RESAMPLE;
            break;
        default:
            m_status = false;
            m_error = ERR_INVALID_SAMPLING;
            return false;
    }

    if (!sid.set_sampling_parameters(lastSystemClockHz, sampleMethod, lastSampleRateHz)) {
        m_status = false;
        m_error = ERR_UNSUPPORTED_FREQ;
        return false;
    }
    return true;
}

void ReSidEmu::sampling(float systemclock, float freq, SidConfig::sampling_method_t method) {
    lastSystemClockHz = systemclock;
    lastSampleRateHz = freq;
    requestedSampling = method;

    if (!applySamplingConfiguration()) {
        return;
    }

    delete[] m_buffer;
    bufferSize = static_cast<int>(std::ceil((freq / 1000.0f) * 20.0f));
    m_buffer = new short[bufferSize];
    m_status = true;
}

void ReSidEmu::model(SidConfig::sid_model_t model, bool digiboost) {
    currentModel = model;
    reSID::chip_model chipModel;
    short sample = 0;
    voiceMask &= 0x07;

    switch (model) {
        case SidConfig::MOS6581:
            chipModel = reSID::MOS6581;
            break;
        case SidConfig::MOS8580:
            chipModel = reSID::MOS8580;
            if (digiboost) {
                voiceMask |= 0x08;
                sample = -32768;
            }
            break;
        default:
            m_status = false;
            m_error = ERR_INVALID_CHIP;
            return;
    }

    sid.set_chip_model(chipModel);
    sid.set_voice_mask(voiceMask);
    sid.input(sample);

    if (lastSystemClockHz > 0.0f && lastSampleRateHz > 0.0f) {
        if (!applySamplingConfiguration()) {
            return;
        }
    }

    m_status = true;
}

} // namespace libsidplayfp
