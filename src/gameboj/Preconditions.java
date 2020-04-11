package gameboj;

/**
 * Utilitaries methods for argument checking
 * 
 * @author Francois BURGUET 288683
 * @author Gaietan Renault 283350
 *
 */
public interface Preconditions {

	/**
	 * Checks if the premise is true or not
	 *
	 * @param b : premise to evaluate
	 * @throws IllegalArgumentException if the argument is false
	 */
	static void checkArgument(boolean b) {
		if (!b)
			throw new IllegalArgumentException();
	}

	/**
	 * Verifies that the integer is between 0 and 0xFF
	 *
	 * @param v : integer to check
	 * @return the parameter
	 * @throws IllegalArgumentException if the parameter is not an 8 bit value
	 */
	static int checkBits8(int v) {
		if (0xFF == (0xFF | v))
			return v;
		else
			throw new IllegalArgumentException();
	}

	/**
	 * Verifies that the integer is between 0 and 0xFFFF
	 *
	 * @param v : integer to check
	 * @return the parameter
	 * @throws IllegalArgumentException if the parameter is not an 16 bit value
	 */
	static int checkBits16(int v) {
		if (0xFFFF == (0xFFFF | v))
			return v;
		else
			throw new IllegalArgumentException();
	}

	static boolean inBounds(int val, int lo, int hi) {
		return lo <= val && val < hi;
	}
}
