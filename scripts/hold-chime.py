#!/usr/bin/env python3
"""Generates the chime that plays when a hold timer ends (app/src/main/res/raw/hold_chime.ogg).

Synthesised instead of downloaded so the sound has no licence to track. Four quick glockenspiel notes
rising E-G#-B-E: bright and short, so it stands apart from message sounds and still carries through a
phone speaker lying on the floor. Chosen by ear over kantele, bell, birdsong and beep variants.

    scripts/hold-chime.py /tmp/hold_chime.wav
    ffmpeg -y -i /tmp/hold_chime.wav -c:a libvorbis -q:a 5 app/src/main/res/raw/hold_chime.ogg
"""
import math
import struct
import sys
import wave

RATE = 44100
TAU = 2 * math.pi
LENGTH = 1.4
# A struck metal bar's overtones sit at inharmonic ratios and die out faster than the fundamental,
# which is what makes it sound like a glockenspiel rather than a sine beep: (ratio, gain, extra decay).
PARTIALS = [(1, 1.0, 0), (2.76, 0.5, 8), (5.40, 0.25, 16)]
# (frequency Hz, start s, ring s): quick steps up, the top note left to ring.
NOTES = [(1318.5, 0.00, 0.5), (1661.2, 0.12, 0.5), (1975.5, 0.24, 0.5), (2637.0, 0.36, 1.0)]


def bar(t: float, freq: float, ring: float) -> float:
    if t < 0 or t > ring:
        return 0.0
    attack = min(1.0, t / 0.002)
    decay = math.exp(-3.5 * t / ring)
    return attack * decay * sum(g * math.sin(TAU * r * freq * t) * math.exp(-k * t) for r, g, k in PARTIALS)


def main(path: str) -> None:
    samples = [sum(bar(i / RATE - start, f, ring) for f, start, ring in NOTES) for i in range(int(RATE * LENGTH))]
    fade = int(RATE * 0.05)
    for i in range(fade):
        samples[-1 - i] *= i / fade
    # Near full scale: the notification volume is the only loudness the user controls.
    scale = 0.89 * 32767 / max(abs(s) for s in samples)
    with wave.open(path, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(b"".join(struct.pack("<h", int(s * scale)) for s in samples))


if __name__ == "__main__":
    main(sys.argv[1])
