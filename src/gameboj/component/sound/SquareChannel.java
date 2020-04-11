package gameboj.component.sound;

import gameboj.Register;
import gameboj.RegisterFile;
import gameboj.bits.Bits;
import gameboj.component.Clocked;
import gameboj.component.Component;

import static gameboj.Preconditions.checkArgument;
import static gameboj.Preconditions.checkBits16;

public abstract class SquareChannel implements Component, Clocked {
    private final short[] DUTY_CYCLES = { 0b00000001, 0b10000001, 0b10000111, 0b01111110};

    protected enum Reg implements Register { NR0, NR1, NR2, NR3, NR4 }
    protected final RegisterFile<Reg> regFile = new RegisterFile<>(Reg.values());

    private boolean enabledFlag;


    private boolean envelopeEnabled;
    private int envelopeTimer;
    private int volumeCounter;

    private int timer;
    private int lengthCounter;
    private int dutyPosition;
    private int outputVolume;

    public void step() {
        timer--;
        if (timer <= 0) {
            timer = (2048 - getTimerLoad()) * 4;
            dutyPosition = (dutyPosition + 1) % 8;
        }
        if (isEnabled() && Bits.test(DUTY_CYCLES[getDuty()], dutyPosition)) outputVolume = volumeCounter;
        else outputVolume = 0;
    }

    public void trigger() {
        enabledFlag = true;
        if (lengthCounter == 0) lengthCounter = 64;

        timer = (2048 - getTimerLoad()) * 4;
        envelopeEnabled = true;
        envelopeTimer = getEnvelopPeriodLoad();
        volumeCounter = getVolumeLoad();
        if (!dacEnabled()) enabledFlag = false;
    }

    public void clockLengthCounter() {
        if (lengthEnabled() && lengthCounter > 0) {
            if (--lengthCounter <= 0) enabledFlag = false;
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

    @Override
    public void cycle(long cycle) {
        checkArgument(cycle < 8);
        if (cycle % 2 == 0) clockLengthCounter();
        if (cycle == 7) clockEnvelope();
    }

    public boolean isEnabled() {
        return enabledFlag && dacEnabled();
    }

    // NR1
    public int getDuty() {
        return (regFile.get(Reg.NR1) >> 6) & 0x3;
    }

    public int getLengthLoad() {
        return regFile.get(Reg.NR1) & 0x3F;
    }

    // NR2
    public int getVolumeLoad() {
        return (regFile.get(Reg.NR2) >> 4) & 0xF;
    }

    public int getEnvelopeAddMode() {
        return (regFile.get(Reg.NR2) >> 3) & 0x1;
    }

    public int getEnvelopPeriodLoad() {
        return regFile.get(Reg.NR2) & 0x7;
    }

    // NR3
    public int getTimerLoad() {
        return ((regFile.get(Reg.NR4) & 0x7) << 8) | (regFile.get(Reg.NR3) & 0xFF);
    }

    public void setTimerLoad(int freq) {
        checkBits16(freq);
        regFile.set(Reg.NR3, freq & 0xFF);
        regFile.set(Reg.NR4, (getTrigger() << 7) | ((lengthEnabled() ? 1 : 0) << 6) | ((freq >> 8) & 0x3));
    }

    // NR4
    public boolean lengthEnabled() {
        return Bits.test(regFile.get(Reg.NR4), 6);
    }

    public void setLengthEnable(boolean enabled) {
        regFile.set(Reg.NR4, Bits.set(regFile.get(Reg.NR4), 6, enabled) & 0xFF);
    }

    public int getTrigger() {
        return (regFile.get(Reg.NR4) >> 7) & 0x1;
    }

    public void setEnvelopPeriod(int period) {
        envelopeTimer = period & 0x7;
    }

    public void setVolumeCounter(int vol) {
        volumeCounter = vol;
    }

    public int getOutputVolume() {
        return outputVolume;
    }

    public boolean dacEnabled() {
        return (regFile.get(Reg.NR2) & 0xF8) != 0;
    }
}