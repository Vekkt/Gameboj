package gameboj.component;

/**
 * Represents a clocked object
 * 
 * @author Francois BURGUET 288683
 * @author Gaietan Renault 283350
 */

public interface Clocked {

	/**
	 * Updates the object during the specified cycle
	 * 
	 * @param cycle : current cycle simulated
	 */
	void cycle(long cycle);

}
