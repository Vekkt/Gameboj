package gameboj.component.sound;

import static gameboj.AddressMap.*;
import static gameboj.Preconditions.*;

public class SquareB extends SquareChannel {
    @Override
    public int read(int address) {
        checkBits16(address);

        if (inBounds(address, REGS_CH2_START, REGS_CH2_END)) {
            Reg reg = Reg.values()[address - REGS_CH2_START];
            return regFile.get(reg);
        } else return NO_DATA;
    }

    @Override
    public void write(int address, int data) {
        checkBits16(address);
        checkBits8(data);

        if (inBounds(address, REGS_CH2_START, REGS_CH2_END)) {
            regFile.set(Reg.values()[address - REGS_CH2_START], data);
//
            switch (Reg.values()[address - REGS_CH2_START]) {
                case NR2:
                    setEnvelopPeriod(getEnvelopPeriodLoad());
                    setVolumeCounter(getVolumeLoad());
                    break;
                case NR4:
                    if (getTrigger() == 1) trigger();
            }
        }
    }
}
