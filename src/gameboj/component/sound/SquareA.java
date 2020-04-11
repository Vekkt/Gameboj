package gameboj.component.sound;

import gameboj.bits.Bits;

import static gameboj.AddressMap.*;
import static gameboj.Preconditions.*;

public final class SquareA extends SquareChannel {
    private int sweepTimer;
    private int sweepShadow;
    private boolean sweepEnable;
    private boolean overFlowFlag;

    @Override
    public int read(int address) {
        checkBits16(address);

        if (inBounds(address, REGS_CH1_START, REGS_CH1_END)) {
            Reg reg = Reg.values()[address - REGS_CH1_START];
            return regFile.get(reg);
        } else return NO_DATA;
    }

    @Override
    public void write(int address, int data) {
        checkBits16(address);
        checkBits8(data);

        if (inBounds(address, REGS_CH1_START, REGS_CH1_END)) {
            regFile.set(Reg.values()[address - REGS_CH1_START], data);

            switch (Reg.values()[address - REGS_CH1_START]) {
                case NR2:
                    setEnvelopPeriod(getEnvelopPeriodLoad());
                    setVolumeCounter(getVolumeLoad());
                    break;
                case NR4:
                    if (getTrigger() == 1) trigger();
            }
        }
    }

    @Override
    public void cycle(long cycle) {
        super.cycle(cycle);
        if (cycle == 2 || cycle == 6) clockSweep();
    }

    @Override
    public void trigger() {
        super.trigger();

        sweepShadow = getTimerLoad();
        sweepTimer = getSweepPeriodLoad();
        sweepEnable = sweepTimer > 0 || getSweepShift() > 0;
        if(sweepTimer == 0) sweepTimer = 8;
        if (getSweepShift() > 0) computeSweep();
    }

    public void clockSweep() {
        sweepTimer--;
        if (sweepTimer <= 0) {
            sweepTimer = getSweepPeriodLoad();
            if (sweepTimer == 0) sweepTimer = 8;

            if(sweepEnable && getSweepPeriodLoad() > 0) {
                int newSweep = computeSweep();
                if (newSweep <= 2047 && getSweepShift() > 0) {
                    sweepShadow = newSweep;
                    setTimerLoad(sweepShadow);
                    computeSweep();
                }
            }
        }

    }

    private int computeSweep() {
        int newSweep = sweepShadow >> getSweepShift();
        if (sweepNegate()) newSweep = sweepShadow - newSweep;
        else newSweep = sweepShadow + newSweep;

        if (newSweep > 2047) overFlowFlag = true;
        return newSweep;
    }

    @Override
    public boolean isEnabled() {
        boolean val = super.isEnabled();
        return val && sweepEnable;
    }

    public boolean sweepEnabled() {
        return !overFlowFlag;
    }

    // NR0
    public int getSweepPeriodLoad() {
        return (regFile.get(Reg.NR0) >> 4) & 0x7;
    }

    public boolean sweepNegate() {
        return Bits.test(regFile.get(Reg.NR0), 3);
    }

    public int getSweepShift() {
        return regFile.get(Reg.NR0) & 0x7;
    }
}
