package gameboj.bits;

/**
 * Represents a Bit
 * 
 * @author Francois Burguet 288683
 * @author Gaietan RENAULT 283350
 */
public interface Bit {
	abstract int ordinal();

	/**
	 * Returns the index of the bit
	 * 
	 * @return index : the index of the bit
	 */
	default int index() {
		return ordinal();
	}

	/**
	 * Returns the mask corresponding to the bit
	 * 
	 * @return mask : mask corresponding to the bit
	 */
	default int mask() {
		return 1 << ordinal();
	}
}
