package gameboj.component.memory;

import gameboj.AddressMap;
import gameboj.component.Component;
import gameboj.component.cartridge.Cartridge;

/**
 * Represents a boot rom controller
 * 
 * @author Francois BURGUET 288683
 * @author Gaietan Renault 283350
 */
public final class BootRomController implements Component {

	private Cartridge cartridge;
	private boolean activated;

	/**
	 * Constructor of BootRomController, initializes the cartridge
	 * 
	 * @param cartridge : a virtual cartridge
	 * @throws NullPointerException if cartridge is null
	 */
	public BootRomController(Cartridge cartridge) {
		if (cartridge == null) {
			throw new NullPointerException();
		}
		this.cartridge = cartridge;
		activated = true;
	}

	/**
	 * Reads a 16 bits address; intercepts if it's a 8 bits address and boot memory
	 * isn't activated
	 * 
	 * @param address : address to read at, 16 bits value
	 * @return the data, 8 bit value
	 * @throws IllegalArgumentException if the address is not a 16 bit value
	 */
	@Override
	public int read(int address) {
		if (0 <= address && address <= 0xFF && activated) {
			return Byte.toUnsignedInt(BootRom.DATA[address]);
		} else {
			return cartridge.read(address);
		}
	}

	/**
	 * Writes data at 16 bits address, disables the boot memory depending if address
	 * equals FF50
	 * 
	 * @param address : address to write at, 16 bits value
	 * @param data : the data to write at address
	 * @throws IllegalArgumentException if the address is not a 16 bit value or if the data is not an 8
	 *             bit value
	 */
	@Override
	public void write(int address, int data) {
		if (address == AddressMap.REG_BOOT_ROM_DISABLE) {
			activated = false;
		} else {
			cartridge.write(address, data);
		}
	}

}
