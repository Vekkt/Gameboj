package gameboj.component.lcd;

import java.util.Objects;

import gameboj.Preconditions;
import gameboj.bits.BitVector;
import gameboj.bits.Bits;

public final class LcdImageLine {

	private final BitVector msb;
	private final BitVector lsb;
	private final BitVector opa;

	private final static int ID_PALETTE = 0b11_10_01_00;

	/**
	 * Creates a new LcdImageLine based on its three components
	 * 
	 * @param msb : the most significant bits of the pixels of the line
	 * @param lsb : the least significant bits of the pixels of the line
	 * @param opacity : the opacity of the pixels of the line
	 * @throws IllegalArgumentException if the vectors are not of the same size
	 */
	public LcdImageLine(BitVector msb, BitVector lsb, BitVector opacity) {
		Preconditions.checkArgument(msb.size() == lsb.size() && lsb.size() == opacity.size());
		this.msb = msb;
		this.lsb = lsb;
		this.opa = opacity;
	}

	/**
	 * Returns the size of the line
	 * 
	 * @return the size
	 */
	public int size() {
		return msb.size();
	}

	/**
	 * Returns the vector of most significant bits of the pixels of the line
	 * 
	 * @return the most significant bits
	 */
	public BitVector msb() {
		return msb;
	}

	/**
	 * Returns the vector of least significant bits of the pixels of the line
	 * 
	 * @return the least significant bits
	 */
	public BitVector lsb() {
		return lsb;
	}

	/**
	 * Returns the vector of the opacity of the pixels of the line
	 * 
	 * @return the opacity
	 */
	public BitVector opacity() {
		return opa;
	}

	/**
	 * Creates a new LcdImageLine, corresponding to the shifted line on a specified
	 * distance. Positive distance is a left shift, right shift if negative.
	 * 
	 * @param distance : the distance of the shift
	 * @return the shifted line
	 */
	public LcdImageLine shift(int distance) {
		return new LcdImageLine(msb.shift(distance), lsb.shift(distance), opa.shift(distance));
	}

	/**
	 * Creates a new LcdImageLine of specified size, corresponding to the extracted
	 * line, starting at a specified bit, with wrap extention convention.
	 * 
	 * @param start : starting bit of the extraction
	 * @param size : size of the extraction
	 * @return the extracted line
	 */
	public LcdImageLine extractWrapped(int start, int size) {
		return new LcdImageLine(
				msb.extractWrapped(start, size),
				lsb.extractWrapped(start, size),
				opa.extractWrapped(start, size));
	}

	/**
	 * Maps the colors of the lines according to the specified palette
	 * 
	 * @param palette : the palette of colors
	 * @return the new mapped line
	 * @throws IllegalArgumentException if the palette is not an 8 bit value
	 */
	public LcdImageLine mapColors(int palette) {
		Preconditions.checkBits8(palette);
		if (palette == ID_PALETTE)
			return this;

		BitVector newMSB = new BitVector(size());
		BitVector newLSB = new BitVector(size());

		for (int i = 0; i < 7; i += 2) {
			BitVector colorMSB = msb;
			BitVector colorLSB = lsb;

			if (!Bits.test(ID_PALETTE, i))
				colorLSB = lsb.not();
			if (!Bits.test(ID_PALETTE, i + 1))
				colorMSB = msb.not();

			BitVector extr = colorMSB.and(colorLSB);

			newMSB = newMSB.or(extr.and(new BitVector(size(), Bits.test(palette, i))));
			newLSB = newLSB.or(extr.and(new BitVector(size(), Bits.test(palette, i + 1))));
		}

		return new LcdImageLine(newMSB, newLSB, opa);
	}

	/**
	 * Creates a new line corresponding to the merge of the current line with the
	 * specified line. Non transparent pixels of the parameter are kept, else they
	 * are replaced by the pixels of the current line.
	 * 
	 * @param above : the line to merge
	 * @return the new merged line
	 */
	public LcdImageLine below(LcdImageLine above) {
		return new LcdImageLine(
				above.msb.and(above.opa).or(this.msb.and(above.opa.not())),
				above.lsb.and(above.opa).or(this.lsb.and(above.opa.not())),
				this.opa.or(above.opa));
	}

	/**
	 * Creates a new line corresponding to the merge of the current line with the
	 * specified line, according to the opacity parameter. Non transparent pixels of
	 * the parameter are kept, else they are replaced by the pixels of the current
	 * line.
	 * 
	 * @param above : the line to merge
	 * @return the new merged line
	 */
	public LcdImageLine below(LcdImageLine above, BitVector opacity) {
		return new LcdImageLine(
				above.msb.and(opacity).or(this.msb.and(opacity.not())),
				above.lsb.and(opacity).or(this.lsb.and(opacity.not())),
				this.opa.or(opacity));
	}

	/**
	 * Creates a new line corresponding to the concatenation of the current line
	 * with the specified line. The left part (first pixels) of specified size is
	 * made from the current line, the right part is made from the specified line.
	 * 
	 * @param line : line to concatenate
	 * @param size : size of the current line part
	 * @return the concatenation of the lines
	 * @throws IllegalArgumentException if the size is negative or greater than the line size
	 */
	public LcdImageLine join(LcdImageLine line, int size) {
		Preconditions.checkArgument(0 <= size && size <= size());
		BitVector maskLeft = (new BitVector(size(), true)).shift(size - size());
		BitVector maskRight = (new BitVector(size(), true)).shift(size);
		return new LcdImageLine(
				msb.and(maskLeft).or(line.msb.and(maskRight)),
				lsb.and(maskLeft).or(line.lsb.and(maskRight)),
				opa.and(maskLeft).or(line.opa.and(maskRight)));
	}

	@Override
	public int hashCode() {
		return Objects.hash(msb, lsb, opa);
	}

	@Override
	public boolean equals(Object that) {
		if (!(that instanceof LcdImageLine))
			return false;
		LcdImageLine line = (LcdImageLine) that;
		return msb.equals(line.msb) && lsb.equals(line.lsb) && opa.equals(line.opa);
	}

	/**
	 * Builder of the class LcdImageLine Allows building a line byte by byte, of the
	 * most significant bits and of the least significant bits separatly. All non
	 * white pixels are considered non transparent.
	 */
	public static final class Builder {
		private BitVector.Builder msbBuilder;
		private BitVector.Builder lsbBuilder;

		/**
		 * Creates a new LcdImageLine builder
		 * 
		 * @param size : size of the line, multiple of 32, non negative
		 */
		public Builder(int size) {
			msbBuilder = new BitVector.Builder(size);
			lsbBuilder = new BitVector.Builder(size);
		}

		/**
		 * Sets the bytes at the specified index to the specified values respectivly of
		 * the most significant bits and of the least significant bits
		 * 
		 * @param index : index of the bytes
		 * @param highB : most significant bits bytes
		 * @param lowB : least significant bits bytes
		 */
		public Builder setBytes(int index, int highB, int lowB) {
			msbBuilder.setByte(index, highB);
			lsbBuilder.setByte(index, lowB);
			return this;
		}

		/**
		 * Builds a new LcdImageLine with its current attributes
		 * 
		 * @return the new line
		 */
		public LcdImageLine build() {
			BitVector msb = msbBuilder.build();
			BitVector lsb = lsbBuilder.build();
			return new LcdImageLine(msb, lsb, msb.or(lsb));
		}
	}

}
