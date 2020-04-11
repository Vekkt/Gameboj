package gameboj;

/**
 * Represents a register
 * 
 * @author Francois Burguet 288683
 * @author Gaietan RENAULT 283350
 */
public interface Register {
	int ordinal();

	/**
	 * Returns the index of the register in the register file
	 * 
	 * @return the index of the register
	 */
	default int index() {
		return ordinal();
	}
}
