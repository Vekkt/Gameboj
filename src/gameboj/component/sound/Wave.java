package gameboj.component.sound;

import gameboj.Register;
import gameboj.RegisterFile;
import gameboj.bits.Bits;
import gameboj.component.Clocked;
import gameboj.component.Component;
import gameboj.component.memory.Ram;
import gameboj.component.memory.RamController;

import static gameboj.AddressMap.*;
import static gameboj.Preconditions.*;

public class Wave implements Component, Clocked {
    protected enum Reg implements Register { NR0, NR1, NR2, NR3, NR4 }
    protected final RegisterFile<Reg> regFile = new RegisterFile<>(Reg.values());

    private final static int[] READ_BACKS = { 0x7F, 0xFF, 0x9F, 0xFF, 0xBF };
    private final static int[] VOLUME_SHIFTS = { 4, 0, 1, 2 };

    private final RamController WaveRAMController = new RamController(
            new Ram(REG_WAVE_TAB_SIZE),
            REG_WAVE_TAB_START,
            REG_WAVE_TAB_END);

    private boolean enabledFlag;
    private int lengthCounter;

    private int tickSinceLastRead;
    private int timer;
    private int posCounter;
    private int outputVolume;

    @Override
    public int read(int address) {
        checkBits16(address);
        if (inBounds(address, REGS_CH3_START, REGS_CH3_END)) {
            int offset = address - REGS_CH3_START;
            return regFile.get(Reg.values()[offset]);
        } else if (inBounds(address, REG_WAVE_TAB_START, REG_WAVE_TAB_END)) {
            int data;
            if (isEnabled() | tickSinceLastRead < 2) {
                int offset = posCounter / 2;
                tickSinceLastRead = 0;
                data = WaveRAMController.read(REG_WAVE_TAB_START + offset);
            } else if (!isEnabled()) {
                tickSinceLastRead = 0;
                data = WaveRAMController.read(address);
            } else data = 0xFF;
            System.out.println(String.format("Read   0x%x    @0x%x", data, address));
            return data;
        }
        return NO_DATA;
    }

    @Override
    public void write(int address, int data) {
        checkBits16(address);
        checkBits8(data);

        if (inBounds(address, REGS_CH3_START, REGS_CH3_END)) {
            regFile.set(Reg.values()[address - REGS_CH3_START], data);
            switch (Reg.values()[address - REGS_CH3_START]) {
                case NR1: lengthCounter = 256 - data; break;
                case NR4: if (Bits.test(data, 7)) trigger();
            }
        } else if (inBounds(address, REG_WAVE_TAB_START, REG_WAVE_TAB_END))
            WaveRAMController.write(address, data);
    }


    @Override
    public void cycle(long cycle) {
        checkArgument(cycle < 8);
        if (cycle % 2 == 0) clockLengthCounter();
    }

    public void step() {
        tickSinceLastRead++;
        if (--timer <= 0) {
            timer = (2048 - getTimerLoad()) * 2;
            posCounter = (posCounter + 1) % REG_WAVE_TAB_SIZE;
            if (isEnabled()) {
                int offset = posCounter / 2;
                int sampleBuffer = Bits.extract(WaveRAMController.read(REG_WAVE_TAB_START + offset),
                        4 * (posCounter % 2),
                        4);
                outputVolume = sampleBuffer >> VOLUME_SHIFTS[getVolumeCode()];
                tickSinceLastRead = 0;
            } else outputVolume = 0;
        }
    }

    public void clockLengthCounter() {
        if (lengthEnabled() && lengthCounter > 0) {
            if (--lengthCounter <= 0) enabledFlag = false;
        }
    }

    public void trigger() {
        enabledFlag = true;
        if (lengthCounter <= 0) lengthCounter = 256;
        timer = (2-48 - getTimerLoad()) * 2;
        posCounter = 0;
        if (!dacEnabled()) enabledFlag = false;
    }

    private boolean isEnabled() {
        return enabledFlag && dacEnabled();
    }

    public int getOutputVolume() {
        return outputVolume;
    }

    // NR0
    public boolean dacEnabled() {
        return Bits.test(regFile.get(Reg.NR0), 7);
    }

    // NR1
    public int getLengthLoad() {
        return regFile.get(Reg.NR1) & 0xFF;
    }

    // NR2
    public int getVolumeCode() {
        return (regFile.get(Reg.NR2) >> 5) & 0x3;
    }

    // NR3
    public int getTimerLoad() {
        return ((regFile.get(Reg.NR4) & 0x7) << 8) | (regFile.get(Reg.NR3) & 0xFF);
    }

    // NR4
    public boolean lengthEnabled() {
        return Bits.test(regFile.get(Reg.NR4), 6);
    }

    public boolean triggered() {
        return Bits.test(regFile.get(Reg.NR4), 7);
    }
}
