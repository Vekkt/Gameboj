package gameboj.component.cartridge;

import java.nio.file.Files;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import gameboj.Preconditions;
import gameboj.component.Component;
import gameboj.component.memory.Rom;

/**
 * Represents a cartridge of type 0
 * 
 * @author Francois BURGUET 288683
 * @author Gaietan Renault 283350
 */
public final class Cartridge implements Component {

	private static String cartridgeName;
	private final MBC1 mbc;
	private final static int[] RAM_SIZE = { 0, 2048, 8192, 32768 };

	private Cartridge(MBC1 mbc1) {
		this.mbc = mbc1;
	}

	/**
	 * Creates a cartridge from the specified ROM file. Must be of type 0.
	 * 
	 * @param romFile : a ROM file
	 * @return a cartridge : a cartrige usable by the GameBoy
	 * @throws IOException if a problem is encountered during the reading
	 * @throws IllegalArgumentException if the ROM is not of type 0
	 */
	public static Cartridge ofFile(File romFile) throws IOException {
		InputStream stream = new FileInputStream(romFile);
		cartridgeName = romFile.getName();

		try {
			byte[] data = Files.readAllBytes(romFile.toPath());
			return new Cartridge(new MBC1(new Rom(data), RAM_SIZE[data[0x149]], cartridgeName));
		} finally {
			stream.close();
		}
	}

	public String getName() {
		return cartridgeName;
	}

	@Override
	public int read(int address) {
		Preconditions.checkBits16(address);
		return mbc.read(address);
	}

	@Override
	public void write(int address, int data) {
		Preconditions.checkBits16(address);
		Preconditions.checkBits8(data);
		mbc.write(address, data);
	}
	public void saveGame() {
		mbc.save();
	}

}
