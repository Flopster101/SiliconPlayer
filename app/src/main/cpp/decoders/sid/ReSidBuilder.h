#ifndef SILICONPLAYER_RESIDBUILDER_H
#define SILICONPLAYER_RESIDBUILDER_H

#include <sidplayfp/sidbuilder.h>

class ReSidBuilder : public sidbuilder {
public:
    explicit ReSidBuilder(const char* name);
    ~ReSidBuilder() override;

    void filter(bool enable);
    void bias(double dacBias);

protected:
    libsidplayfp::sidemu* create() override;
    const char* getCredits() const override;

private:
    struct Config;
    Config* config;
};

#endif // SILICONPLAYER_RESIDBUILDER_H
