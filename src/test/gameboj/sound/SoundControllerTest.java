package test.gameboj.sound;

import gameboj.AddressMap;
import gameboj.Bus;
import gameboj.component.sound.SoundController;
import gameboj.component.sound.SoundOutput;
import org.junit.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SoundControllerTest {
    @Test
    public void canWriteAndReadInAllSoundRegisters() {
    }

    @Test
    public void canWriteAndReadInSquareOne() {

        Bus bus = new Bus();
        SoundOutput output = new SoundOutput();
        SoundController apu = new SoundController(output);

        apu.attachTo(bus);

        int[] data = { 0x11, 0x22, 0x33, 0x44, 0x55 };

        boolean ok = true;

        for (int address = AddressMap.REGS_CH1_START; address < AddressMap.REGS_CH1_END; address++) {
            apu.write(address, data[address - AddressMap.REGS_CH1_START]);
        }

        for (int address = AddressMap.REGS_CH1_START; address < AddressMap.REGS_CH1_END; address++) {
            int val = apu.read(address);
            System.out.println(String.format("0x%x", val));
            ok = ok && (val == data[address - AddressMap.REGS_CH1_START]);
        }
        assertTrue(ok);
    }

}