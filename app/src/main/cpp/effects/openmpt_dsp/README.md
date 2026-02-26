# OpenMPT DSP Attribution

This folder contains DSP processing code integrated into Silicon Player's native audio pipeline.

## Attribution

The design and parameter model for these effects were adapted from the OpenMPT project (https://openmpt.org), primarily from the desktop sound DSP sources:

- `sounddsp/DSP.h`
- `sounddsp/DSP.cpp`
- `sounddsp/Reverb.h`
- `sounddsp/Reverb.cpp`

Upstream tree in this repository:

- `repo/external/libopenmpt/`

## License

OpenMPT is distributed under the BSD-3-Clause license.

A copy of the license is provided in `LICENSE.OpenMPT` in this folder.

## Notes

- This integration currently targets shared processing in `AudioEngine`.
- Behavior is adapted for mobile runtime constraints and may not be bit-identical
  to desktop OpenMPT output.
