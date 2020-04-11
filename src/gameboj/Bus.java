package gameboj;

import java.util.ArrayList;
import java.util.Objects;

import gameboj.component.Component;

import static gameboj.AddressMap.REG_WAVE_TAB_END;
import static gameboj.AddressMap.REG_WAVE_TAB_START;
import static gameboj.Preconditions.inBounds;

/**
 * Represents a physical bus
 * 
 * @author Francois BURGUET 288683
 * @author Gaietan Renault 283350
 */

public final class Bus {

	private ArrayList<Component> comp = new ArrayList<>();

	/**
	 * Add a component to the bus
	 * 
	 * @param component : component to add, non-null
	 * @throws NullPointerException
	 */
	public void attach(Component component) {
		comp.add(Objects.requireNonNull(component));
	}

	/**
	 * Returns the component knowing his address in the list
	 * 
	 * @param address : address to read at, 16 bits
	 * @throws IllegalArgumentException if invalid address
	 * @return first data non NO_DATA in components at address
	 */
	public int read(int address) {
		Preconditions.checkBits16(address);

		for (Component c : comp) {
			if (c.read(address) != Component.NO_DATA)
				return c.read(address);
		}
		return 0xFF;

	}

	/**
	 * Writes data to the given adress
	 * 
	 * @param address : address of the data, 16 bits
	 * @param data : data to write, 8 bits
	 * @throws IllegalArgumentException if invalid address or data
	 */
	public void write(int address, int data) {
		Preconditions.checkBits16(address);
		Preconditions.checkBits8(data);

		for (Component c : comp) {
			c.write(address, data);
		}
	}
}
