package gameboj.component;

import gameboj.Bus;

/**
 * Represents a physical component
 * 
 * @author Francois BURGUET 288683
 * @author Gaietan Renault 283350
 */
public interface Component {

	/**
	 * Value for no data in memory
	 */
	int NO_DATA = 0x100;

	/**
	 * Reads the data at the specified address in the component
	 * 
	 * @param address : address to read at, 16 bits value
	 * @return the data, 8 bit value
	 * @throws IllegalArgumentException if the address is not a 16 bit value
	 */
	int read(int address);

	/**
	 * Writes the data at the specified address in the memory
	 * 
	 * @param address : address to write at, 16 bits value
	 * @param data : the data to write at address
	 * @throws IllegalArgumentException if the address is not a 16 bit value or if the data is not an 8
	 *             bit value
	 */
	void write(int address, int data);

	/**
	 * Attach the component to the bus
	 * 
	 * @param bus : to bus to attach the component to
	 */
	default void attachTo(Bus bus) {
		bus.attach(this);
	}
}
