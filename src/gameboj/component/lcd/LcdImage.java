package gameboj.component.lcd;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import gameboj.Preconditions;

public final class LcdImage {

	private final int width;
	private final int height;
	private final List<LcdImageLine> lines;

	/**
	 * Creates a new LCD image, of specified width and height, based on a list of
	 * LCD image lines.
	 *
	 * @param width : the width of the image
	 * @param height : the height of the image
	 * @param lines : lines that compose the image
	 * @throws IllegalArgumentException : if one of the dimensions is negative or the list given does not
	 *             match the dimensions
	 */

	public LcdImage(int width, int height, List<LcdImageLine> lines) {
		Preconditions.checkArgument(width > 0 && height > 0);
		Preconditions.checkArgument(lines.size() == height && lines.get(0).size() == width);
		this.width = width;
		this.height = height;
		this.lines = new ArrayList<>(lines);
	}

	/**
	 * Returns the width of the image
	 * 
	 * @return the width
	 */
	public int width() {
		return width;
	}

	/**
	 * Returns the height of the image
	 * 
	 * @return the height
	 */
	public int height() {
		return height;
	}

	/**
	 * Returns the color of the pixel at specified coordinates
	 * 
	 * @param x : the horizontal coordinate
	 * @param y : the vertical coordinate
	 * @return the color of the pixel
	 */
	public int get(int x, int y) {
		int c1 = lines.get(y).msb().testBit(x) ? 1 : 0;
		int c0 = lines.get(y).lsb().testBit(x) ? 1 : 0;
		return c0 + c1 * 2;
	}

	@Override
	public int hashCode() {
		return Objects.hash(lines);
	}

	@Override
	public boolean equals(Object that) {
		if (!(that instanceof LcdImage))
			return false;
		LcdImage image = (LcdImage) that;
		return lines.equals(image.lines);
	}

	/**
	 * Builder of the class LcdImage Allows building the imgae line by line
	 */
	public static final class Builder {
		private int width;
		private int height;
		private List<LcdImageLine> lines;

		/**
		 * Creates a new LcdImage builder
		 * 
		 * @param width : the width of the image
		 * @param height : the height of the image
		 * @throws IllegalArgumentException if one of the dimensions is negative
		 */
		public Builder(int width, int height, int specialWidth, int specialHeight) {
			Preconditions.checkArgument(width > 0 && height > 0);
			this.width = width;
			this.height = height;

			this.lines = new ArrayList<>();
			for (int i = 0; i < height; i++)
				lines.add(new LcdImageLine.Builder(width).build());
		}

		public Builder(int width, int height) {
			this(width, height, width, height);
		}

		/**
		 * Sets the line at the specified index to the line given in parameters
		 * 
		 * @param index : index of the line to set
		 * @param line : the line to put at index
		 * @throws IllegalArgumentException if the lines does not match the dimensions of the image
		 */
		public void setLine(int index, LcdImageLine line) {
			Preconditions.checkArgument(line.size() == lines.get(0).size());
			lines.set(index, line);
		}

		/**
		 * Builds a new LcdImage with its current attributes
		 * 
		 * @return an LcdImage
		 */
		public LcdImage build() {
			return new LcdImage(width, height, lines);
		}

	}

}
