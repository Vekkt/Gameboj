package gameboj.component.cpu;

import gameboj.Preconditions;
import gameboj.bits.Bit;
import gameboj.bits.Bits;

/**
 * Implements the arithmetic and logical part of the CPU
 * 
 * @author Francois BURGUET 288683
 * @author Gaietan Renault 283350
 */

public final class Alu {

	private Alu() {
	}

	/**
	 * Flag type, taking 8 posible values: 4 unsued, 4 corresponding to ZNHC
	 */
	public enum Flag implements Bit {
		UNUSED_0, UNUSED_1, UNUSED_2, UNUSED_3, C, H, N, Z
	}

	/**
	 * RotDir type, specifies the direction of the rotation
	 */
	public enum RotDir {
		LEFT, RIGHT
	}

	private static int flagMask(Flag flag, boolean enabled) {
		return enabled ? flag.mask() : 0;
	}

	/**
	 * Returns the mask of the flags
	 * 
	 * @param z : Z flag
	 * @param n : N flag
	 * @param h : H flag
	 * @param c : C flag
	 * @return 8 bit value, in the format ZNHC000
	 */
	public static int maskZNHC(boolean z, boolean n, boolean h, boolean c) {
		return flagMask(Flag.Z, z) | flagMask(Flag.N, n) | flagMask(Flag.H, h) | flagMask(Flag.C, c);
	}

	/**
	 * Unpacks the value contained in the parameter
	 * 
	 * @param valueFlags : int package of data value + flag values
	 * @return the 8 or 16 bit value packed in valueFlags
	 */
	public static int unpackValue(int valueFlags) {
		return valueFlags >>> 8;
	}

	/**
	 * Unpacks the flags contained in the parameter
	 * 
	 * @param valueFlags : int package of data value + flag values
	 * @return the 8 bit value containing the flags
	 */
	public static int unpackFlags(int valueFlags) {
		return Bits.clip(8, valueFlags);
	}

	/**
	 * Adds two 8 bits values and a potential carry
	 * 
	 * @param l : left operand, an 8 bit value
	 * @param r : right operand, an 8 bit value
	 * @param c0 : possible carry
	 * @return an int package, containing the result and the flags Z0HC
	 * @throws IllegalArgumentException if one of the operand is not an 8 bit value
	 */
	public static int add(int l, int r, boolean c0) {
		Preconditions.checkBits8(l);
		Preconditions.checkBits8(r);
		int value = Bits.clip(8, l + r + (c0 ? 1 : 0));
		boolean h = Bits.clip(4, l) + Bits.clip(4, r) + Bits.clip(4, (c0 ? 1 : 0)) > 0x0F;
		boolean c = l + r + (c0 ? 1 : 0) > 0xFF;
		return packValueZNHC(value, value == 0, false, h, c);
	}

	/**
	 * Adds two 8 bits values with no carry
	 * 
	 * @param l : left operand, an 8 bit value
	 * @param r : right operand, an 8 bit value
	 * @return an int package, containing the result and the flags Z0HC
	 * @throws IllegalArgumentException if one of the operand is not an 8 bit value
	 */
	public static int add(int l, int r) {
		return add(l, r, false);
	}

	/**
	 * Adds two 16 bits values. The flag H and C are computed for the 8 LSB of the
	 * values
	 * 
	 * @param l : left operand, an 16 bit value
	 * @param r : right operand, an 16 bit value
	 * @return an int package, containing the result and the flags 00HC
	 * @throws IllegalArgumentException if one of the operand is not a 16 bit value
	 */
	public static int add16L(int l, int r) {
		Preconditions.checkBits16(l);
		Preconditions.checkBits16(r);
		int value = l + r;
		boolean h = Bits.clip(4, l) + Bits.clip(4, r) > 0xF;
		boolean c = Bits.clip(8, l) + Bits.clip(8, r) > 0xFF;
		return Bits.clip(24, packValueZNHC(value, false, false, h, c));
	}

	/**
	 * Adds two 16 bits values. The flag H and C are computed for the 8 HSB of the
	 * values
	 * 
	 * @param l : left operand, an 16 bit value
	 * @param r : right operand, an 16 bit value
	 * @return an int package, containing the result and the flags 00HC
	 * @throws IllegalArgumentException if one of the operand is not a 16 bit value
	 */
	public static int add16H(int l, int r) {
		Preconditions.checkBits16(l);
		Preconditions.checkBits16(r);
		int value = l + r;
		int lowC = Bits.clip(8, l) + Bits.clip(8, r) > 0xFF ? 1 : 0;
		boolean h = Bits.extract(l, 8, 4) + Bits.extract(r, 8, 4) + lowC > 0xF;
		boolean c = Bits.extract(l, 8, 8) + Bits.extract(r, 8, 8) + lowC > 0xFF;
		return Bits.clip(24, packValueZNHC(value, false, false, h, c));
	}

	/**
	 * Difference between two 8 bits values and a carry
	 * 
	 * @param l : left operand, an 8 bit value
	 * @param r : right operand, an 8 bit value
	 * @param b0 : possible carry
	 * @return an int package, containing the result and the flags Z1HC
	 * @throws IllegalArgumentException if one of the operand is not an 8 bit value
	 */
	public static int sub(int l, int r, boolean b0) {
		Preconditions.checkBits8(l);
		Preconditions.checkBits8(r);
		int value = Bits.clip(8, l - r - (b0 ? 1 : 0));
		boolean h = Bits.clip(4, r) + (b0 ? 1 : 0) > Bits.clip(4, l);
		boolean c = r + (b0 ? 1 : 0) > l;
		return packValueZNHC(value, value == 0, true, h, c);
	}

	/**
	 * Difference between two 8 bits values without a carry
	 * 
	 * @param l : left operand, an 8 bit value
	 * @param r : right operand, an 8 bit value
	 * @return an int package, containing the result and the flags Z1HC
	 * @throws IllegalArgumentException if one of the operand is not an 8 bit value
	 */
	public static int sub(int l, int r) {
		return sub(l, r, false);
	}

	/**
	 * Adjusts the value to the BCD format
	 * 
	 * @param v : value to convert, 8 bits value
	 * @param n : N flag
	 * @param h : H flag
	 * @param c : C falg
	 * @return an int package, containing the adjusted value and the flags ZN0H
	 * @throws IllegalArgumentException if the value to convert is not an 8 bit value
	 */
	public static int bcdAdjust(int v, boolean n, boolean h, boolean c) {
		Preconditions.checkBits8(v);
		boolean fixL = h | (!n & Bits.clip(4, v) > 9);
		boolean fixH = c | (!n & v > 0x99);
		int fix = 0x60 * (fixH ? 1 : 0) + 0x06 * (fixL ? 1 : 0);
		int Va = v - (n ? fix : (-fix));
		Va = Bits.clip(8, Va);
		return packValueZNHC(Va, Va == 0, n, false, fixH);
	}

	/**
	 * Computes the logical and between the two parameters
	 * 
	 * @param l : left operand, an 8 bit value
	 * @param r : right operand, an 8 bit value
	 * @return an int package, containing the result value and the flags Z010
	 * @throws IllegalArgumentException if one of the operand is not an 8 bit value
	 */
	public static int and(int l, int r) {
		Preconditions.checkBits8(l);
		Preconditions.checkBits8(r);
		int v = l & r;
		return packValueZNHC(v, v == 0, false, true, false);
	}

	/**
	 * Computes the logical or between the two parameters
	 * 
	 * @param l : left operand, an 8 bit value
	 * @param r : right operand, an 8 bit value
	 * @return an int package, containing the result value and the flags Z000
	 * @throws IllegalArgumentException if one of the operand is not an 8 bit value
	 */
	public static int or(int l, int r) {
		Preconditions.checkBits8(l);
		Preconditions.checkBits8(r);
		int v = l | r;
		return packValueZNHC(v, v == 0, false, false, false);
	}

	/**
	 * Computes the logical xor between the two parameters
	 * 
	 * @param l : left operand, an 8 bit value
	 * @param r : right operand, an 8 bit value
	 * @return an int package, containing the result value and the flags Z000
	 * @throws IllegalArgumentException if one of the operand is not an 8 bit value
	 */
	public static int xor(int l, int r) {
		Preconditions.checkBits8(l);
		Preconditions.checkBits8(r);
		int v = l ^ r;
		return packValueZNHC(v, v == 0, false, false, false);
	}

	/**
	 * Shifts the binary value by one to the left. The C flag corresponds to the
	 * ejected value.
	 * 
	 * @param v : the value to shift, an 8 bit value
	 * @return an int package, containing the shifted value and the flags Z00C
	 * @throws IllegalArgumentException if the value to shift is not an 8 bit value
	 */
	public static int shiftLeft(int v) {
		Preconditions.checkBits8(v);
		int value = Bits.clip(8, v << 1);
		return packValueZNHC(value, value == 0, false, false, (v & 0x80) == 0x80);
	}

	/**
	 * Arithemtic shifts the binary value by one to the right. The C flag
	 * corresponds to the ejected value.
	 * 
	 * @param v : the value to shift, an 8 bit value
	 * @return an int package, containing the shifted value and the flags Z00C
	 * @throws IllegalArgumentException if the value to shift is not an 8 bit value
	 */
	public static int shiftRightA(int v) {
		Preconditions.checkBits8(v);
		int value = Bits.clip(8, Bits.signExtend8(v) >> 1);
		return packValueZNHC(value, value == 0, false, false, (v & 0x1) == 0x1);
	}

	/**
	 * Logical shifts the binary value by one to the right. The C flag corresponds
	 * to the ejected value.
	 * 
	 * @param v : the value to shift, an 8 bit value
	 * @return an int package, containing the shifted value and the flags Z00C
	 * @throws IllegalArgumentException if the value to shift is not an 8 bit value
	 */
	public static int shiftRightL(int v) {
		Preconditions.checkBits8(v);
		int value = Bits.clip(8, v >>> 1);
		return packValueZNHC(value, value == 0, false, false, (v & 0x1) == 0x1);
	}

	/**
	 * Rotates the binary value by one to the specified direction. The C flag
	 * corresponds to the value that changed of side.
	 * 
	 * @param d : direction of the rotation
	 * @param v : value to rotate, an 8 bit value
	 * @return an int package, containing the rotated value and the flags Z00C
	 * @throws IllegalArgumentException if the value to rotate is not an 8 bit value
	 */
	public static int rotate(RotDir d, int v) {
		Preconditions.checkBits8(v);
		int dir = d == RotDir.LEFT ? 1 : -1;
		int value = Bits.rotate(8, v, dir);
		int p = d == RotDir.LEFT ? 0x80 : 0x01;
		boolean c = (v & p) == p;
		return packValueZNHC(value, value == 0, false, false, c);
	}

	/**
	 * Rotates the binary value through the carry, by one to the specified
	 * direction. The C flag corresponds to the value that changed of side.
	 * 
	 * @param d : direction of the rotation
	 * @param v : value to rotate, an 8 bit value
	 * @param c : possible carry
	 * @return an int package, containing the rotated value and the flags Z00C
	 * @throws IllegalArgumentException if the value to rotate is not an 8 bit value
	 */
	public static int rotate(RotDir d, int v, boolean c) {
		Preconditions.checkBits8(v);
		int dir = d == RotDir.LEFT ? 1 : -1;
		int val = Bits.make16(c ? 1 : 0, v);
		val = Bits.rotate(9, val, dir);
		boolean C = (val & 0x100) == 0x100;
		val = Bits.clip(8, val);
		return packValueZNHC(val, val == 0, false, false, C);
	}

	/**
	 * Swaps the first 4 bits with bits 5 to 8.
	 * 
	 * @param v : the value to swap
	 * @return the swapped value
	 * @throws IllegalArgumentException if the value to swap is not an 8 bit value
	 */
	public static int swap(int v) {
		Preconditions.checkBits8(v);
		int left = v & 0b1111;
		int right = v & 0b11110000;
		int val = (left << 4) + (right >>> 4);
		return packValueZNHC(val, val == 0, false, false, false);
	}

	/**
	 * Returns flags Z010, with Z true if and only if the bit at the specified index
	 * is a 0
	 * 
	 * @param v : the value to test, an 8 bit value
	 * @param bitIndex : the index of the bit to test, an int between 0 and 7
	 * @return an int package, with value 0 and flags Z010
	 * @throws IndexOutOfBoundsException if the index is negative or greater (or equal) than 8
	 * @throws IllegalArgumentException if the value to test is not an 8 bit value
	 */
	public static int testBit(int v, int bitIndex) {
		if (0 > bitIndex || bitIndex > 7) {
			throw new IndexOutOfBoundsException();
		}
		Preconditions.checkBits8(v);
		boolean z = !Bits.test(v, bitIndex);
		return packValueZNHC(0, z, false, true, false);
	}

	/**
	 * Packs the value and the flags in a 32 bits value
	 * 
	 * @param v : the value to pack with the flags
	 * @param z : Z flag
	 * @param n : N flag
	 * @param h : H flag
	 * @param c : C flag
	 * @return the package containing the value and the flags
	 */
	public static int packValueZNHC(int v, boolean z, boolean n, boolean h, boolean c) {
		return (v << 8) + maskZNHC(z, n, h, c);
	}
}
