package gameboj.bits;

import java.util.Arrays;

import gameboj.Preconditions;

/**
 * Represents a vector of bits, of length multiple of Integer.SIZE
 * 
 * @author Francois BURGUET 288683
 * @author Gaietan Renault 283350
 */

public final class BitVector {

	private enum booleanOp {
		OR, AND
	};

	private enum extractOp {
		ZERO, WRAP
	};

	private final int[] vector;

	/**
	 * Constructs a new vector of bits of the specified size where all bits are
	 * initialized at the specified value
	 * 
	 * @param size : size of the vector, multiple of Integer.SIZE
	 * @param val : value at which the bits should be initialized
	 * @throws IllegalArgumentException if the size is not striclty positive or not a multiple of Integer.SIZE
	 */
	public BitVector(int size, boolean val) {
		Preconditions.checkArgument(size % Integer.SIZE == 0 && size > 0);

		vector = new int[size / Integer.SIZE];
		if (val)
			Arrays.fill(vector, -1);
	}

	/**
	 * Constructs a new vector of bits of the specified size where all bits are
	 * initialized at zero
	 * 
	 * @param size : size of the vector, multiple of Integer.SIZE, strictly positive
	 */
	public BitVector(int size) {
		this(size, false);
	}

	private BitVector(int[] vector) {
		this.vector = vector;
	}

	@Override
	public String toString() {
		String vect = "";
		for (int i : this.vector)
			vect = String.format("%32s", Integer.toBinaryString(i)).replace(' ', '0') + vect;
		return vect;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof BitVector)
			return false;
		BitVector vect = (BitVector) o;
		return (this.size() == vect.size() && vector.equals(vect.vector));
	}

	@Override
	public int hashCode() {
		return vector.hashCode();
	}

	/**
	 * Returns the size, in bits, of the vector
	 * 
	 * @return the size of the vector
	 */
	public int size() {
		return vector.length * Integer.SIZE;
	}

	/**
	 * Tests the bit at the specified index in the vector
	 * 
	 * @param index : index of the bit to test
	 * @return true if the bit is a 1, false if it's a 0
	 * @throws IllegalArgumentException if the index is negative or greater (or equal) than the vector size
	 */
	public boolean testBit(int index) {
		Preconditions.checkArgument(0 <= index && index < size());
		return Bits.test(vector[index / Integer.SIZE], index % Integer.SIZE);
	}

	/**
	 * Creates a new vector corresponding ot the logic negation of the current
	 * vector
	 * 
	 * @return a new vector, logic negation of the current one
	 */
	public BitVector not() {
		int[] newVect = new int[size() / Integer.SIZE];
		for (int i = 0; i < size() / Integer.SIZE; i++)
			newVect[i] = ~vector[i];
		return new BitVector(newVect);
	}

	/**
	 * Returns a new vector corresponding to the logic conjunction between the
	 * current vector and the parameter
	 * 
	 * @param vect : the second operand
	 * @return a new vector, conjunction of the current vector and the parameter
	 * @throws IllegalArgumentException if the two vectors are not of the same size
	 */
	public BitVector and(BitVector vect) {
		return booleanOperation(booleanOp.AND, vect);
	}

	/**
	 * Returns a new vector corresponding to the logic disjunction between the
	 * current vector and the parameter
	 * 
	 * @param vect : the second operand
	 * @return a new vector, disjunction of the current vector and the parameter
	 * @throws IllegalArgumentException if the two vectors are not of the same size
	 */
	public BitVector or(BitVector vect) {
		return booleanOperation(booleanOp.OR, vect);
	}

	/**
	 * Extracts from the current vector a new vector, starting from start, of
	 * specified size, with the zero extended convention
	 * 
	 * @param start : starting bit in the extended vector
	 * @param size : size of the extraction
	 * @return a new vector, the extrected part of the extended vector
	 * @throws IllegalArgumentException if the size is not a multiple of Integer.SIZE
	 */
	public BitVector extractZeroExtended(int start, int size) {
		return extract(extractOp.ZERO, start, size);
	}

	/**
	 * Extracts from the current vector a new vector, starting from start, of
	 * specified size, with the wrap extended convention
	 * 
	 * @param start : starting bit in the extended vector
	 * @param size : size of the extraction
	 * @return a new vector, the extracted part of the extended vector
	 * @throws IllegalArgumentException if the size is not a multiple of Integer.SIZE
	 */
	public BitVector extractWrapped(int start, int size) {
		return extract(extractOp.WRAP, start, size);
	}

	/**
	 * Creates a new vector corresponding to the shifted current vector on a
	 * specified distance
	 * 
	 * @param distance : distance of the shift, to the left if positive, else to the right
	 * @return a new shifted vector
	 * @throws IllegalArgumentException if the two vectors are not of the same size
	 */
	public BitVector shift(int distance) {
		return extractZeroExtended(distance, size());
	}

	private BitVector booleanOperation(booleanOp operator, BitVector vect) {
		Preconditions.checkArgument(this.size() == vect.size());
		int[] newVect = new int[size() / Integer.SIZE];

		for (int i = 0; i < size() / Integer.SIZE; i++) {
			newVect[i] = operator == booleanOp.AND ? this.vector[i] & vect.vector[i] : this.vector[i] | vect.vector[i];
		}
		return new BitVector(newVect);
	}

	private BitVector extract(extractOp type, int start, int size) {
		Preconditions.checkArgument(size % Integer.SIZE == 0 && size >= 0);
		int[] newVect = new int[size / Integer.SIZE];

		if (start % Integer.SIZE == 0) {
			for (int i = start; i < start + size; i += Integer.SIZE)
				newVect[(i - start) / Integer.SIZE] = getChunk(type, i);
		} else {
			for (int i = start; i < start + size; i += Integer.SIZE) {
				int prevChunk = getChunk(type, i);
				int nextChunk = getChunk(type, i + Integer.SIZE);
				int shift = Math.floorMod(start, Integer.SIZE);
				newVect[(i - start) / Integer.SIZE] = nextChunk << (Integer.SIZE - shift) | prevChunk >>> shift;
			}
		}
		return new BitVector(newVect);
	}

	private int getChunk(extractOp type, int index) {
		if (type == extractOp.ZERO && !(0 <= index && index < size()))
			return 0;
		return this.vector[Math.floorMod(Math.floorDiv(index, Integer.SIZE), size() / Integer.SIZE)];
	}

	/**
	 * Static builder of the class BitVector Allows construction of a vector, byte
	 * by byte
	 */
	public final static class Builder {
		private int[] vector;

		/**
		 * Creates a new builder for the current instance of BitVector
		 * 
		 * @param size : size of the vector in bits
		 */
		public Builder(int size) {
			Preconditions.checkArgument(size % Integer.SIZE == 0 && size > 0);
			vector = new int[size / Integer.SIZE];
		}

		/**
		 * Sets the desired byte to the specified value
		 * 
		 * @param index : index of the byte
		 * @param val : value of the byte
		 * @return the builder itself
		 * @throws IllegalStateException if the vector ahs already been built
		 * @throws IndexOutOfBoundsException if the index of the byte is negative or greater (or equal) to the
		 *         number of possible bytes in the vector
		 * @throws IllegalArgumentException if the byte is not an 8 bit value
		 */
		public Builder setByte(int index, int val) {
			if (vector == null)
				throw new IllegalStateException();
			if (index < 0 || vector.length * 4 <= index)
				throw new IndexOutOfBoundsException();
			Preconditions.checkBits8(val);

			for (int i = 0; i < Byte.SIZE; i++) {
				boolean b = Bits.test(val, i);
				vector[index / 4] = Bits.set(vector[index / 4], (index * Byte.SIZE) % Integer.SIZE + i, b);
			}
			return this;
		}

		/**
		 * Buids a new vector from the builder
		 * 
		 * @return a new vector, corresponding to the buider
		 */
		public BitVector build() {
			if (vector == null)
				throw new IllegalStateException();
			BitVector bitVector = new BitVector(vector);
			vector = null;
			return bitVector;
		}
	}

}
