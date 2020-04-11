package gameboj.component;

import gameboj.AddressMap;
import gameboj.Preconditions;
import gameboj.bits.Bits;
import gameboj.component.cpu.Cpu;
import gameboj.component.cpu.Cpu.Interrupt;

import java.util.Objects;

/**
 * Implements the keyboard of the GameBoy
 * 
 * @author Francois BURGUET 288683
 * @author Gaietan Renault 283350
 */

public class Joypad implements Component {

	private final Cpu cpu;
	private int keysLine1 = 0xF;
	private int keysLine2 = 0xF;

	private int P1 = 0xFF;

	public enum Key {
		RIGHT, LEFT, UP, DOWN, A, B, SELECT, START
	}

	/**
	 * Creates a new joypad for the specified cpu
	 * @param cpu, non null
	 * @throws NullPointerException if the CPU is null
	 */
	public Joypad(Cpu cpu) {
		this.cpu = Objects.requireNonNull(cpu);
	}

	@Override
	public int read(int address) {
		Preconditions.checkBits16(address);
		if (address == AddressMap.REG_P1) {
			return P1;
		}
		return NO_DATA;
	}

	@Override
	public void write(int address, int data) {
		Preconditions.checkBits16(address);
		Preconditions.checkBits8(data);
		if (address == AddressMap.REG_P1) {
			P1 = 0xC0 + (Bits.extract(data, 4, 2) << 4);
			if (!Bits.test(P1, 4))
				P1 = P1 | Bits.clip(4, keysLine1);
			if (!Bits.test(P1, 5))
				P1 = P1 | Bits.clip(4, keysLine2);
			if (Bits.test(P1, 4) && Bits.test(P1, 5))
				P1 = 0xFF;
		}
	}

	/**
	 * Fires JOYPAD interrupt on the CPU and updates the key value
	 * @param key, the key pressed
	 */
	public void keyPressed(Key key) {
		if (key != null) {
			if (key.ordinal() < 4) {
				if (Bits.test(keysLine1, key.ordinal()))
					cpu.requestInterrupt(Interrupt.JOYPAD);
				keysLine1 = Bits.set(keysLine1, key.ordinal(), false);
			}
			if (key.ordinal() >= 4) {
				if (Bits.test(keysLine2, key.ordinal() - 4))
					cpu.requestInterrupt(Interrupt.JOYPAD);
				keysLine2 = Bits.set(keysLine2, key.ordinal() - 4, false);
			}
		}
	}

	/**
	 * Updates the key value
	 * @param key, the key released
	 */
	public void keyReleased(Key key) {
		if (key != null) {
			if (key.ordinal() < 4)
				keysLine1 = Bits.set(keysLine1, key.ordinal(), true);
			else
				keysLine2 = Bits.set(keysLine2, key.ordinal() - 4, true);
		}
	}
}
