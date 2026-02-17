#include "ReSidBuilder.h"

#include <new>

#include "ReSidEmu.h"
#include "properties.h"

struct ReSidBuilder::Config {
    Property<double> bias;
    Property<bool> filterEnabled;
};

ReSidBuilder::ReSidBuilder(const char* name)
        : sidbuilder(name),
          config(new Config) {}

ReSidBuilder::~ReSidBuilder() {
    remove();
    delete config;
}

libsidplayfp::sidemu* ReSidBuilder::create() {
    try {
        auto* sid = new libsidplayfp::ReSidEmu(this);
        if (config->bias.has_value()) {
            sid->bias(config->bias.value());
        }
        if (config->filterEnabled.has_value()) {
            sid->filter(config->filterEnabled.value());
        }
        return sid;
    } catch (const std::bad_alloc&) {
        m_errorBuffer.assign(name()).append(" ERROR: Unable to create ReSID object");
        return nullptr;
    }
}

const char* ReSidBuilder::getCredits() const {
    return libsidplayfp::ReSidEmu::getCredits();
}

void ReSidBuilder::filter(bool enable) {
    config->filterEnabled = enable;
    for (libsidplayfp::sidemu* emu : sidobjs) {
        static_cast<libsidplayfp::ReSidEmu*>(emu)->filter(enable);
    }
}

void ReSidBuilder::bias(double dacBias) {
    config->bias = dacBias;
    for (libsidplayfp::sidemu* emu : sidobjs) {
        static_cast<libsidplayfp::ReSidEmu*>(emu)->bias(dacBias);
    }
}
