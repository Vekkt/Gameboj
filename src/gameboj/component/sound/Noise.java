package gameboj.component.sound;

import gameboj.Register;
import gameboj.RegisterFile;
import gameboj.bits.Bits;
import gameboj.component.Clocked;
import gameboj.component.Component;

import static gameboj.AddressMap.*;
import static gameboj.Preconditions.*;
import static gameboj.Preconditions.inBounds;

public class Noise implements Component, Clocked {
    protected enum Reg implements Register { NR40, NR41, NR42, NR43, NR44}
    protected final RegisterFile<Reg> regFile = new RegisterFile<>(Reg.values());

    private final static int[] READ_BACKS = { 0xFF, 0xFF, 0x00, 0x00, 0xBF };
    private final static short[] DIVISORS = { 8, 16, 32, 48, 64, 80, 96, 112 };

    private boolean enabledFlag;
    private int lengthCounter;

    private int envelopeTimer;
    private int volumeCounter;
    private boolean envelopeEnabled;

    private int timer;
    private int outputVolume;

    private int LFSR;

    @Override
    public int read(int address) {
        checkBits16(address);
        if (inBounds(address, REGS_CH4_START, REGS_CH4_END)) {
            int offset = address - REGS_CH4_START;
            return regFile.get(Reg.values()[offset]);
        } else return NO_DATA;
    }

    @Override
    public void write(int address, int data) {
        checkBits16(address);
        checkBits8(data);

        if (inBounds(address, REGS_CH4_START, REGS_CH4_END)) {
            regFile.set(Reg.values()[address - REGS_CH4_START], data);
            switch (Wave.Reg.values()[address - REGS_CH4_START]) {
                case NR1: lengthCounter = 64 - data; break;
                case NR4: if (Bits.test(data, 7)) trigger();
            }
        }
    }

    @Override
    public void cycle(long cycle) {
        checkArgument(cycle < 8);
        if (cycle % 2 == 0) clockLengthCounter();
        if (cycle == 7) clockEnvelope();
    }

    public void trigger() {
        enabledFlag = true;
        if (lengthCounter <= 0) lengthCounter = 64;
        timer = DIVISORS[getDivisorCode()] << getClockShift();
        envelopeTimer = getEnvelopPeriodLoad();
        envelopeEnabled = true;
        volumeCounter = getVolumeLoad();
        LFSR = 0x7FFF;
        if (!dacEnabled()) enabledFlag = false;
    }

    public void step() {
        if (--timer <= 0) {
            timer = DIVISORS[getDivisorCode()] << getClockShift();

             int result = (LFSR & 0x1) ^ ((LFSR & 0x10) >> 1);
             Bits.set(LFSR, 15, result == 1);
             if (widthModeSet()) Bits.set(LFSR, 6, result == 1);
             outputVolume = (1 & ~LFSR) * volumeCounter;
        }
    }

    public void clockEnvelope() {
        if (--envelopeTimer <= 0 && getEnvelopPeriodLoad() > 0) {
            envelopeTimer = getEnvelopPeriodLoad();
            if (envelopeEnabled) {
                if ((volumeCounter == 0 && getEnvelopeAddMode() == -1)
                        || (volumeCounter == 15 && getEnvelopeAddMode() == 1)) {
                    envelopeEnabled = false;
                    return;
                }
                volumeCounter += getEnvelopeAddMode();
            }
        }
    }

    public void clockLengthCounter() {
        if (lengthEnabled() && lengthCounter > 0) {
            if (--lengthCounter <= 0) enabledFlag = false;
        }
    }

    public int getOutputVolume() {
        if (isEnabled()) return outputVolume;
        else return 0;
    }

    public boolean isEnabled() {
        return enabledFlag && dacEnabled();
    }

    // NR1
    public int getLengthLoad() {
        return regFile.get(Reg.NR41) & 0x3F;
    }

    // NR2
    public int getVolumeLoad() {
        return (regFile.get(Reg.NR42) >> 4) & 0xF;
    }

    public int getEnvelopeAddMode() {
        return Bits.test(regFile.get(Reg.NR42), 3) ? 1 : -1;
    }

    public int getEnvelopPeriodLoad() {
        return regFile.get(Reg.NR42) & 0x7;
    }

    // NR3
    public int getClockShift() {
        return (regFile.get(Reg.NR43) >> 4) & 0xF;
    }

    public boolean widthModeSet() {
        return Bits.test(regFile.get(Reg.NR43), 3);
    }

    public int getDivisorCode() {
        return regFile.get(Reg.NR43) & 0x7;
    }

    // NR4
    public boolean lengthEnabled() {
        return Bits.test(regFile.get(Reg.NR44), 6);
    }

    public boolean triggered() {
        return Bits.test(regFile.get(Reg.NR44), 7);
    }

    public boolean dacEnabled() {
        return (regFile.get(Reg.NR42) & 0xF8) != 0;
    }

}
