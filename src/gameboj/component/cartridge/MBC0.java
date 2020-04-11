package gameboj.component.cartridge;

import gameboj.Preconditions;
import gameboj.component.Component;
import gameboj.component.memory.Rom;

/**
 * Represents a Memory Boot Controller of type 0
 * 
 * @author Francois BURGUET 288683
 * @author Gaietan Renault 283350
 */
public final class MBC0 implements Component {

	private final Rom rom;
	private final String romName;

	/**
	 * Initiliaze a MCB of type 0
	 * 
	 * @param rom : a ROM
	 * @throws NullPointerException if the ROM is null
	 */
	public MBC0(Rom rom) {
		this(rom, null);
	}

	public MBC0(Rom rom, String romName) {
		if (rom == null) {
			throw new NullPointerException();
		}
		Preconditions.checkArgument(rom.size() == 0x8000);
		this.rom = rom;
		this.romName = romName;
	}

	/**
	 * Reads the data at the specified address in the MBC0 if the address is smaller
	 * than 0xFF
	 * 
	 * @param address : address to read at, 16 bits value
	 * @return the data, 8 bit value
	 * @throws IllegalArgumentException if the address is not a 16 bit value
	 */
	@Override
	public int read(int address) {
		Preconditions.checkBits16(address);
		return 0 <= address && address < 0x8000 ? rom.read(address) : NO_DATA;
	}

	/**
	 * Does nothing
	 */
	@Override
	public void write(int address, int data) {
	}

	public String getName() {
		return romName;
	}
}
