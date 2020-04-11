package gameboj.component.cpu;

import gameboj.AddressMap;
import gameboj.Bus;
import gameboj.Preconditions;
import gameboj.Register;
import gameboj.RegisterFile;
import gameboj.bits.Bit;
import gameboj.bits.Bits;
import gameboj.component.Clocked;
import gameboj.component.Component;
import gameboj.component.cpu.Alu.RotDir;
import gameboj.component.memory.Ram;

/**
 * Implements the arithmetic and logical part of the CPU
 * 
 * @author Francois BURGUET 288683
 * @author Gaietan Renault 283350
 */

public final class Cpu implements Component, Clocked {

	public enum Interrupt implements Bit {
		VBLANK, LCD_STAT, TIMER, SERIAL, JOYPAD
	}

	private enum Reg implements Register {
		A, F, B, C, D, E, H, L
	}

	private enum Reg16 implements Register {
		AF, BC, DE, HL
	}

	private enum FlagSrc {
		V0, V1, ALU, CPU
	}

	private int SP = 0;
	private int PC = 0;

	private int IE = 0;
	private int IF = 0;
	private boolean IME = false;

	private Ram highRam = new Ram(AddressMap.HIGH_RAM_END - AddressMap.HIGH_RAM_START);

	private Bus bus;

	private RegisterFile<Reg> regFile = new RegisterFile<>(Reg.values());

	private long nextNonIdleCycle;

	private static final Opcode[] DIRECT_OPCODE_TABLE = buildOpcodeTable(Opcode.Kind.DIRECT);
	private static final Opcode[] PREFIXED_OPCODE_TABLE = buildOpcodeTable(Opcode.Kind.PREFIXED);

	@Override
	public void cycle(long cycle) {
		int interrupt = IE & IF;

		if (interrupt != 0 && nextNonIdleCycle == Long.MAX_VALUE)
			nextNonIdleCycle = cycle;
		else if (cycle != nextNonIdleCycle)
			return;

		reallyCycle();
	}

	private void reallyCycle() {
		int interrupt = IE & IF;
		if (IME && interrupt != 0) {
			IME = false;
			int i = 31 - Integer.numberOfLeadingZeros(Integer.lowestOneBit(interrupt));
			IF = Bits.set(IF, i, false);
			push16(PC);
			PC = AddressMap.INTERRUPTS[i];
			nextNonIdleCycle += 5;
		} else {
			int code = read8(PC);
			Opcode opCode;
			if (code == 0xCB) {
				code = read8AfterOpcode();
				opCode = PREFIXED_OPCODE_TABLE[code];
			} else
				opCode = DIRECT_OPCODE_TABLE[code];

			dispatch(opCode);
		}
	}

	/**
	 * Sets the IF register to handle the specified interruption
	 * 
	 * @param i : the desired interruption
	 */
	public void requestInterrupt(Interrupt i) {
		IF = Bits.set(IF, i.index(), true);
	}

	@Override
	public int read(int address) {
		Preconditions.checkBits16(address);
		if (AddressMap.HIGH_RAM_START <= address && address < AddressMap.HIGH_RAM_END)
			return highRam.read(address - AddressMap.HIGH_RAM_START);
		else if (address == AddressMap.REG_IF)
			return IF;
		else if (address == AddressMap.REG_IE)
			return IE;
		else
			return NO_DATA;
	}

	@Override
	public void write(int address, int data) {
		Preconditions.checkBits16(address);
		Preconditions.checkBits8(data);
		if (AddressMap.HIGH_RAM_START <= address && address < AddressMap.HIGH_RAM_END)
			highRam.write(address - AddressMap.HIGH_RAM_START, data);
		else if (address == AddressMap.REG_IF)
			IF = data;
		else if (address == AddressMap.REG_IE)
			IE = data;
	}

	private void dispatch(Opcode opCode) {

		int nextPC = PC + opCode.totalBytes;

		switch (opCode.family) {
		case NOP: { }
			break;
		// Load
		case LD_R8_HLR: {
			Reg r = extractReg(opCode, 3);
			regFile.set(r, read8AtHl());
		}
			break;
		case LD_A_HLRU: {
			int s = extractHlIncrement(opCode);
			regFile.set(Reg.A, read8AtHl());
			setReg16(Reg16.HL, Bits.clip(16, reg16(Reg16.HL) + s));
		}
			break;
		case LD_A_N8R: {
			int N8 = read8AfterOpcode();
			regFile.set(Reg.A, read8(0xFF00 + N8));
		}
			break;
		case LD_A_CR: {
			int C = regFile.get(Reg.C);
			regFile.set(Reg.A, read8(0xFF00 + C));
		}
			break;
		case LD_A_N16R: {
			int N16 = read16AfterOpcode();
			regFile.set(Reg.A, read8(N16));
		}
			break;
		case LD_A_BCR: {
			int BC = reg16(Reg16.BC);
			regFile.set(Reg.A, read8(BC));
		}
			break;
		case LD_A_DER: {
			int DE = reg16(Reg16.DE);
			regFile.set(Reg.A, read8(DE));
		}
			break;
		case LD_R8_N8: {
			Reg R8 = extractReg(opCode, 3);
			int N8 = read8AfterOpcode();
			regFile.set(R8, N8);
		}
			break;
		case LD_R16SP_N16: {
			Reg16 R16 = extractReg16(opCode);
			int N16 = read16AfterOpcode();
			setReg16SP(R16, N16);
		}
			break;
		case POP_R16: {
			Reg16 R16 = extractReg16(opCode);
			int val = pop16();
			setReg16(R16, val);
		}
			break;

		// Store
		case LD_HLR_R8: {
			int val = regFile.get(extractReg(opCode, 0));
			write8AtHl(val);
		}
			break;
		case LD_HLRU_A: {
			int s = extractHlIncrement(opCode);
			int val = regFile.get(Reg.A);
			write8AtHl(val);
			setReg16(Reg16.HL, Bits.clip(16, reg16(Reg16.HL) + s));
		}
			break;
		case LD_N8R_A: {
			int N8 = read8AfterOpcode();
			write8((0xFF00 + N8), regFile.get(Reg.A));
		}
			break;
		case LD_CR_A: {
			write8(0xFF00 + regFile.get(Reg.C), regFile.get(Reg.A));
		}
			break;
		case LD_N16R_A: {
			int N16 = read16AfterOpcode();
			write8(N16, regFile.get(Reg.A));
		}
			break;
		case LD_BCR_A: {
			int BC = reg16(Reg16.BC);
			write8(BC, regFile.get(Reg.A));
		}
			break;
		case LD_DER_A: {
			int DE = reg16(Reg16.DE);
			write8(DE, regFile.get(Reg.A));
		}
			break;
		case LD_HLR_N8: {
			int N8 = read8AfterOpcode();
			write8(reg16(Reg16.HL), N8);
		}
			break;
		case LD_N16R_SP: {
			int N16 = read16AfterOpcode();
			write16(N16, SP);
		}
			break;
		case PUSH_R16: {
			Reg16 R16 = extractReg16(opCode);
			push16(reg16(R16));
		}
			break;
		case LD_R8_R8: {
			Reg R8 = extractReg(opCode, 3);
			Reg S8 = extractReg(opCode, 0);
			if (R8 != S8) {
				regFile.set(R8, regFile.get(S8));
			}
		}
			break;
		case LD_SP_HL: {
			SP = reg16(Reg16.HL);
		}
			break;

		// Add
		case ADD_A_R8: {
			Reg R8 = extractReg(opCode, 0);
			boolean C = getCarry(opCode, false);
			int vf = Alu.add(regFile.get(Reg.A), regFile.get(R8), C);
			setRegFlags(Reg.A, vf);
		}
			break;
		case ADD_A_N8: {
			int N8 = read8AfterOpcode();
			boolean C = getCarry(opCode, false);
			int vf = Alu.add(regFile.get(Reg.A), N8, C);
			setRegFlags(Reg.A, vf);
		}
			break;
		case ADD_A_HLR: {
			boolean C = getCarry(opCode, false);
			int vf = Alu.add(regFile.get(Reg.A), read8AtHl(), C);
			setRegFlags(Reg.A, vf);
		}
			break;
		case INC_R8: {
			Reg R8 = extractReg(opCode, 3);
			int vf = Alu.add(regFile.get(R8), 1);
			setRegFromAlu(R8, vf);
			combineAluFlags(vf, FlagSrc.ALU, FlagSrc.V0, FlagSrc.ALU, FlagSrc.CPU);
		}
			break;
		case INC_HLR: {
			int vf = Alu.add(read8AtHl(), 1);
			write8(reg16(Reg16.HL), Bits.clip(8, Alu.unpackValue(vf)));
			combineAluFlags(vf, FlagSrc.ALU, FlagSrc.V0, FlagSrc.ALU, FlagSrc.CPU);
		}
			break;
		case INC_R16SP: {
			Reg16 R16 = extractReg16(opCode);
			int val = R16 == Reg16.AF ? SP : reg16(R16);
			int vf = Alu.add16L(val, 1);
			if (R16 == Reg16.AF)
				SP = Alu.unpackValue(vf);
			else
				setReg16SP(R16, Alu.unpackValue(vf));
		}
			break;
		case ADD_HL_R16SP: {
			Reg16 R16 = extractReg16(opCode);
			int val = R16 == Reg16.AF ? SP : reg16(R16);
			int vf = Alu.add16H(reg16(Reg16.HL), val);
			setReg16(Reg16.HL, Alu.unpackValue(vf));
			combineAluFlags(vf, FlagSrc.CPU, FlagSrc.V0, FlagSrc.ALU, FlagSrc.ALU);
		}
			break;
		case LD_HLSP_S8: {
			int S8 = Bits.clip(16, Bits.signExtend8(read8AfterOpcode()));
			int vf = Alu.add16L(SP, S8);
			combineAluFlags(vf, FlagSrc.V0, FlagSrc.V0, FlagSrc.ALU, FlagSrc.ALU);

			if (!Bits.test(opCode.encoding, 4))
				SP = Alu.unpackValue(vf);
			else
				setReg16(Reg16.HL, Alu.unpackValue(vf));
		}
			break;

		// Subtract
		case SUB_A_R8: {
			Reg R8 = extractReg(opCode, 0);
			boolean C = getCarry(opCode, false);
			int vf = Alu.sub(regFile.get(Reg.A), regFile.get(R8), C);
			setRegFlags(Reg.A, vf);
		}
			break;
		case SUB_A_N8: {
			int N8 = read8AfterOpcode();
			boolean C = getCarry(opCode, false);
			int vf = Alu.sub(regFile.get(Reg.A), N8, C);
			setRegFlags(Reg.A, vf);
		}
			break;
		case SUB_A_HLR: {
			boolean C = getCarry(opCode, false);
			int vf = Alu.sub(regFile.get(Reg.A), read8AtHl(), C);
			setRegFlags(Reg.A, vf);
		}
			break;
		case DEC_R8: {
			Reg R8 = extractReg(opCode, 3);
			int vf = Alu.sub(regFile.get(R8), 1);
			setRegFromAlu(R8, vf);
			combineAluFlags(vf, FlagSrc.ALU, FlagSrc.V1, FlagSrc.ALU, FlagSrc.CPU);
		}
			break;
		case DEC_HLR: {
			int vf = Alu.sub(read8AtHl(), 1);
			write8AtHl(Bits.clip(8, Alu.unpackValue(vf)));
			combineAluFlags(vf, FlagSrc.ALU, FlagSrc.V1, FlagSrc.ALU, FlagSrc.CPU);
		}
			break;
		case CP_A_R8: {
			Reg R8 = extractReg(opCode, 0);
			int vf = Alu.sub(regFile.get(Reg.A), regFile.get(R8));
			setFlags(vf);
		}
			break;
		case CP_A_N8: {
			int N8 = read8AfterOpcode();
			int vf = Alu.sub(regFile.get(Reg.A), N8);
			setFlags(vf);
		}
			break;
		case CP_A_HLR: {
			int vf = Alu.sub(regFile.get(Reg.A), read8AtHl());
			setFlags(vf);
		}
			break;
		case DEC_R16SP: {
			Reg16 R16 = extractReg16(opCode);
			if (R16 == Reg16.AF)
				SP = Bits.clip(16, SP - 1);
			else
				setReg16(R16, Bits.clip(16, reg16(R16) - 1));
		}
			break;

		// And, or, xor, complement
		case AND_A_N8: {
			int N8 = read8AfterOpcode();
			int vf = Alu.and(regFile.get(Reg.A), N8);
			setRegFlags(Reg.A, vf);
		}
			break;
		case AND_A_R8: {
			Reg R8 = extractReg(opCode, 0);
			int vf = Alu.and(regFile.get(Reg.A), regFile.get(R8));
			setRegFlags(Reg.A, vf);
		}
			break;
		case AND_A_HLR: {
			int vf = Alu.and(regFile.get(Reg.A), read8AtHl());
			setRegFlags(Reg.A, vf);
		}
			break;
		case OR_A_R8: {
			Reg R8 = extractReg(opCode, 0);
			int vf = Alu.or(regFile.get(Reg.A), regFile.get(R8));
			setRegFlags(Reg.A, vf);
		}
			break;
		case OR_A_N8: {
			int N8 = read8AfterOpcode();
			int vf = Alu.or(regFile.get(Reg.A), N8);
			setRegFlags(Reg.A, vf);
		}
			break;
		case OR_A_HLR: {
			int vf = Alu.or(regFile.get(Reg.A), read8AtHl());
			setRegFlags(Reg.A, vf);
		}
			break;
		case XOR_A_R8: {
			Reg R8 = extractReg(opCode, 0);
			int vf = Alu.xor(regFile.get(Reg.A), regFile.get(R8));
			setRegFlags(Reg.A, vf);
		}
			break;
		case XOR_A_N8: {
			int N8 = read8AfterOpcode();
			int vf = Alu.xor(regFile.get(Reg.A), N8);
			setRegFlags(Reg.A, vf);
		}
			break;
		case XOR_A_HLR: {
			int vf = Alu.xor(regFile.get(Reg.A), read8AtHl());
			setRegFlags(Reg.A, vf);
		}
			break;
		case CPL: {
			boolean z = Bits.test(regFile.get(Reg.F), 7);
			boolean c = Bits.test(regFile.get(Reg.F), 4);
			int v = Bits.complement8(regFile.get(Reg.A));
			int vf = Alu.packValueZNHC(v, z, true, true, c);
			setRegFlags(Reg.A, vf);
		}
			break;

		// Rotate, shift
		case ROTCA: {
			Alu.RotDir dir = extractDirection(opCode);
			int vf = Alu.rotate(dir, regFile.get(Reg.A));
			setRegFromAlu(Reg.A, vf);
			combineAluFlags(vf, FlagSrc.V0, FlagSrc.V0, FlagSrc.V0, FlagSrc.ALU);
		}
			break;
		case ROTA: {
			Alu.RotDir dir = extractDirection(opCode);
			boolean C = Bits.test(regFile.get(Reg.F), 4);
			int vf = Alu.rotate(dir, regFile.get(Reg.A), C);
			setRegFromAlu(Reg.A, vf);
			combineAluFlags(vf, FlagSrc.V0, FlagSrc.V0, FlagSrc.V0, FlagSrc.ALU);
		}
			break;
		case ROTC_R8: {
			Alu.RotDir dir = extractDirection(opCode);
			Reg R8 = extractReg(opCode, 0);
			int vf = Alu.rotate(dir, regFile.get(R8));
			setRegFlags(R8, vf);
		}
			break;
		case ROT_R8: {
			Alu.RotDir dir = extractDirection(opCode);
			Reg R8 = extractReg(opCode, 0);
			boolean C = Bits.test(regFile.get(Reg.F), 4);
			int vf = Alu.rotate(dir, regFile.get(R8), C);
			setRegFlags(R8, vf);
		}
			break;
		case ROTC_HLR: {
			Alu.RotDir dir = extractDirection(opCode);
			int vf = Alu.rotate(dir, read8AtHl());
			write8AtHlAndSetFlags(vf);
		}
			break;
		case ROT_HLR: {
			Alu.RotDir dir = extractDirection(opCode);
			boolean C = Bits.test(regFile.get(Reg.F), 4);
			int vf = Alu.rotate(dir, read8AtHl(), C);
			write8AtHlAndSetFlags(vf);
		}
			break;
		case SWAP_R8: {
			Reg R8 = extractReg(opCode, 0);
			int vf = Alu.swap(regFile.get(R8));
			setRegFlags(R8, vf);
		}
			break;
		case SWAP_HLR: {
			int vf = Alu.swap(read8AtHl());
			write8AtHlAndSetFlags(vf);
		}
			break;
		case SLA_R8: {
			Reg R8 = extractReg(opCode, 0);
			int vf = Alu.shiftLeft(regFile.get(R8));
			setRegFlags(R8, vf);
		}
			break;
		case SRA_R8: {
			Reg R8 = extractReg(opCode, 0);
			int vf = Alu.shiftRightA(regFile.get(R8));
			setRegFlags(R8, vf);
		}
			break;
		case SRL_R8: {
			Reg R8 = extractReg(opCode, 0);
			int vf = Alu.shiftRightL(regFile.get(R8));
			setRegFlags(R8, vf);
		}
			break;
		case SLA_HLR: {
			int vf = Alu.shiftLeft(read8AtHl());
			write8AtHlAndSetFlags(vf);
		}
			break;
		case SRA_HLR: {
			int vf = Alu.shiftRightA(read8AtHl());
			write8AtHlAndSetFlags(vf);
		}
			break;
		case SRL_HLR: {
			int vf = Alu.shiftRightL(read8AtHl());
			write8AtHlAndSetFlags(vf);
		}
			break;

		// Bit test and set
		case BIT_U3_R8: {
			Reg R8 = extractReg(opCode, 0);
			int N3 = Bits.extract(opCode.encoding, 3, 3);
			int vf = Alu.testBit(regFile.get(R8), N3);
			combineAluFlags(vf, FlagSrc.ALU, FlagSrc.ALU, FlagSrc.ALU, FlagSrc.CPU);
		}
			break;
		case BIT_U3_HLR: {
			int HL = read8AtHl();
			int N3 = Bits.extract(opCode.encoding, 3, 3);
			int vf = Alu.testBit(HL, N3);
			combineAluFlags(vf, FlagSrc.ALU, FlagSrc.V0, FlagSrc.V1, FlagSrc.CPU);
		}
			break;
		case CHG_U3_R8: {
			Reg R8 = extractReg(opCode, 0);
			int N3 = Bits.extract(opCode.encoding, 3, 3);
			boolean newV = Bits.test(opCode.encoding, 6);
			regFile.set(R8, Bits.set(regFile.get(R8), N3, newV));
		}
			break;
		case CHG_U3_HLR: {
			int N3 = Bits.extract(opCode.encoding, 3, 3);
			boolean newV = Bits.test(opCode.encoding, 6);
			write8AtHl(Bits.set(read8AtHl(), N3, newV));
		}
			break;

		// Misc. ALU
		case DAA: {
			int vf = Alu.bcdAdjust(regFile.get(Reg.A), Bits.test(regFile.get(Reg.F), 6),
					Bits.test(regFile.get(Reg.F), 5), Bits.test(regFile.get(Reg.F), 4));
			setRegFromAlu(Reg.A, vf);
			combineAluFlags(vf, FlagSrc.ALU, FlagSrc.CPU, FlagSrc.V0, FlagSrc.ALU);
		}
			break;
		case SCCF: {
			boolean C = Bits.test(regFile.get(Reg.F), 4);
			boolean newC = !Bits.test(opCode.encoding, 3) || !C;
			int vf = Alu.packValueZNHC(0, false, false, false, newC);
			combineAluFlags(vf, FlagSrc.CPU, FlagSrc.V0, FlagSrc.V0, FlagSrc.ALU);
		}
			break;

		// Jumps
		case JP_HL: {
			nextPC = reg16(Reg16.HL);
		}
			break;
		case JP_N16: {
			nextPC = read16AfterOpcode();
		}
			break;
		case JP_CC_N16: {
			int N16 = read16AfterOpcode();
			boolean cc = extractCondition(opCode);
			if (cc) {
				nextPC = N16;
				nextNonIdleCycle += opCode.additionalCycles;
			}
		}
			break;
		case JR_E8: {
			int E8 = Bits.signExtend8(read8AfterOpcode());
			nextPC = Bits.clip(16, E8 + nextPC);
		}
			break;
		case JR_CC_E8: {
			int E8 = Bits.signExtend8(read8AfterOpcode());
			boolean cc = extractCondition(opCode);
			if (cc) {
				nextPC = Bits.clip(16, nextPC + E8);
				nextNonIdleCycle += opCode.additionalCycles;
			}
		}
			break;

		// Calls and returns
		case CALL_N16: {
			int N16 = read16AfterOpcode();
			push16(nextPC);
			nextPC = N16;
		}
			break;
		case CALL_CC_N16: {
			int N16 = read16AfterOpcode();
			boolean cc = extractCondition(opCode);
			if (cc) {
				push16(nextPC);
				nextPC = N16;
				nextNonIdleCycle += opCode.additionalCycles;
			}
		}
			break;
		case RST_U3: {
			push16(nextPC);
			nextPC = AddressMap.RESETS[Bits.extract(opCode.encoding, 3, 3)];
		}
			break;
		case RET: {
			nextPC = pop16();
		}
			break;
		case RET_CC: {
			boolean cc = extractCondition(opCode);
			if (cc) {
				nextPC = pop16();
				nextNonIdleCycle += opCode.additionalCycles;
			}
		}
			break;

		// Interrupts
		case EDI: {
			IME = Bits.test(opCode.encoding, 3);
		}
			break;
		case RETI: {
			IME = true;
			nextPC = pop16();
		}
			break;

		// Misc control
		case HALT: {
			nextNonIdleCycle = Long.MAX_VALUE;
		}
			break;
		case STOP:
			throw new Error("STOP is not implemented");
		default: {
		}
			break;
		}

		nextNonIdleCycle += opCode.cycles;
		PC = nextPC;

	}

	@Override
	public void attachTo(Bus bus) {
		bus.attach(this);
		this.bus = bus;
	}

	// ---------------------------------------//
	// Bus access method

	private int read8(int address) {
		return Bits.clip(8, bus.read(address));
	}

	private int read8AtHl() {
		return Bits.clip(8, bus.read(reg16(Reg16.HL)));
	}

	private int read8AfterOpcode() {
		return Bits.clip(8, bus.read(Bits.clip(16, PC + 1)));
	}

	private int read16(int address) {
		return Bits.make16(bus.read(Bits.clip(16, address + 1)), bus.read(Bits.clip(16, address)));
	}

	private int read16AfterOpcode() {
		return read16(Bits.clip(16, PC + 1));
	}

	private void write8(int address, int v) {
		bus.write(address, v);
	}

	private void write16(int address, int v) {
		bus.write(address, Bits.clip(8, v));
		bus.write(Bits.clip(16, address + 1), Bits.extract(v, 8, 8));
	}

	private void write8AtHl(int v) {
		bus.write(reg16(Reg16.HL), v);
	}

	private void push16(int v) {
		SP = Bits.clip(16, SP - 2);
		write16(SP, v);
	}

	private int pop16() {
		int val = read16(SP);
		SP = Bits.clip(16, SP + 2);
		return val;
	}

	// ---------------------------------------//
	// Register pairs management method

	private int reg16(Reg16 r) {
		switch (r) {
		case AF:
			return Bits.make16(regFile.get(Reg.A), regFile.get(Reg.F));
		case BC:
			return Bits.make16(regFile.get(Reg.B), regFile.get(Reg.C));
		case DE:
			return Bits.make16(regFile.get(Reg.D), regFile.get(Reg.E));
		case HL:
			return Bits.make16(regFile.get(Reg.H), regFile.get(Reg.L));
		default:
			throw new IllegalArgumentException();
		}
	}

	private void setReg16(Reg16 r, int newV) {
		Preconditions.checkBits16(newV);
		int lowB = Bits.clip(8, newV);
		int highB = Bits.extract(newV, 8, 8);

		switch (r) {
		case AF:
			lowB &= (((1 << 4) - 1) << 4);
			regFile.set(Reg.A, highB);
			regFile.set(Reg.F, lowB);
			break;
		case BC:
			regFile.set(Reg.B, highB);
			regFile.set(Reg.C, lowB);
			break;
		case DE:
			regFile.set(Reg.D, highB);
			regFile.set(Reg.E, lowB);
			break;
		case HL:
			regFile.set(Reg.H, highB);
			regFile.set(Reg.L, lowB);
			break;
		default:
			throw new IllegalArgumentException();
		}
	}

	private void setReg16SP(Reg16 r, int newV) {
		Preconditions.checkArgument(0 <= newV && newV <= 0xFFFF);
		if (r == Reg16.AF) {
			SP = newV;
		} else {
			setReg16(r, newV);
		}
	}

	// ---------------------------------------//
	// Parameters extraction

	private Reg extractReg(Opcode opcode, int startBit) {
		Preconditions.checkArgument(0 <= startBit && startBit <= 5);
		int pos = Bits.extract(opcode.encoding, startBit, 3);
		Reg[] regTab = { Reg.B, Reg.C, Reg.D, Reg.E, Reg.H, Reg.L, null, Reg.A };
		return regTab[pos];
	}

	private Reg16 extractReg16(Opcode opcode) {
		int pos = Bits.extract(opcode.encoding, 4, 2);
		Reg16[] regTab = { Reg16.BC, Reg16.DE, Reg16.HL, Reg16.AF };
		return regTab[pos];
	}

	private int extractHlIncrement(Opcode opcode) {
		return Bits.test(opcode.encoding, 4) ? -1 : +1;
	}

	private Alu.RotDir extractDirection(Opcode opcode) {
		return !Bits.test(opcode.encoding, 3) ? RotDir.LEFT : RotDir.RIGHT;
	}

	// ---------------------------------------//
	// Flags methods

	private void setRegFromAlu(Reg r, int vf) {
		int val = Alu.unpackValue(vf);
		regFile.set(r, Bits.clip(8, val));
	}

	private void setFlags(int vf) {
		int flags = Alu.unpackFlags(vf);
		flags &= (((1 << 4) - 1) << 4);
		regFile.set(Reg.F, flags);
	}

	private void setRegFlags(Reg r, int vf) {
		setRegFromAlu(r, vf);
		setFlags(vf);
	}

	private void write8AtHlAndSetFlags(int vf) {
		int val = Alu.unpackValue(vf);
		write8(reg16(Reg16.HL), Bits.clip(8, val));
		setFlags(vf);
	}

	private void combineAluFlags(int vf, FlagSrc z, FlagSrc n, FlagSrc h, FlagSrc c) {
		int maskV1 = getMask(FlagSrc.V1, z, n, h, c);
		int maskAlu = getMask(FlagSrc.ALU, z, n, h, c);
		int maskCpu = getMask(FlagSrc.CPU, z, n, h, c);

		maskAlu &= Alu.unpackFlags(vf);
		maskCpu &= regFile.get(Reg.F);
		regFile.set(Reg.F, maskAlu | maskCpu | maskV1);
	}

	private int getMask(FlagSrc f, FlagSrc z, FlagSrc n, FlagSrc h, FlagSrc c) {
		return Alu.maskZNHC(z == f, n == f, h == f, c == f);
	}

	private boolean getCarry(Opcode code, boolean type) {
		if (Bits.test(regFile.get(Reg.F), 4) && Bits.test(code.encoding, 3))
			return !type;
		else
			return type;
	}

	private boolean extractCondition(Opcode opCode) {
		int cc = Bits.extract(opCode.encoding, 3, 2);
		switch (cc) {
		case 0:
			return !Bits.test(regFile.get(Reg.F), 7);
		case 1:
			return Bits.test(regFile.get(Reg.F), 7);
		case 2:
			return !Bits.test(regFile.get(Reg.F), 4);
		case 3:
			return Bits.test(regFile.get(Reg.F), 4);
		default:
			throw new IllegalArgumentException();
		}
	}

	private static Opcode[] buildOpcodeTable(Opcode.Kind kind) {
		Opcode[] opCodeTable = new Opcode[0xFF + 1];
		for (Opcode o : Opcode.values())
			if (o.kind == kind)
				opCodeTable[o.encoding] = o;

		return opCodeTable;
	}
}