package gameboj.component.memory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import gameboj.Preconditions;

/**
 * Represents a physical RAM
 * 
 * @author Francois BURGUET 288683
 * @author Gaietan Renault 283350
 */
public final class Ram {

	private byte[] data;

	/**
	 * Constructor of Ram, creates a data array full of 0
	 * 
	 * @param size : size of the ArrayList
	 */
	public Ram(int size) {
		Preconditions.checkArgument(size >= 0);
		this.data = new byte[size];
	}

	/**
	 * Returns the size of the data array
	 * 
	 * @return size of data
	 */
	public int size() {
		return data.length;
	}

	/**
	 * Returns the byte at index of the data array
	 * 
	 * @param index : index of the byte to read
	 * @return the byte at index in the data array
	 */
	public int read(int index) {
		if (index < 0 || index >= size()) {
			throw new IndexOutOfBoundsException();
		}
		return Byte.toUnsignedInt(data[index]);
	}

	/**
	 * Writes the value to the given index in the data array
	 * 
	 * @param index : index where the value must be written
	 * @param value : 8 bits value to write at the index
	 */
	public void write(int index, int value) {
		Preconditions.checkBits8(value);
		if (index < 0 || index >= size()) {
			throw new IndexOutOfBoundsException();
		} else {
			data[index] = (byte) value;
		}
	}

	public void saveRam(File file) {
		OutputStream out = null;
		try {
			file.getParentFile().mkdirs();
			file.createNewFile();
			out = new FileOutputStream(file);
			out.write(data);
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void loadRam(byte[] data) {
		Preconditions.checkArgument(data.length == this.data.length);
		this.data = data;
	}

}
