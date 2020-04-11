package gameboj.component.lcd;

import java.util.Arrays;
import java.util.Objects;

import gameboj.AddressMap;
import gameboj.Bus;
import gameboj.Register;
import gameboj.RegisterFile;
import gameboj.bits.Bit;
import gameboj.bits.BitVector;
import gameboj.bits.Bits;
import gameboj.component.Clocked;
import gameboj.component.Component;
import gameboj.component.cpu.Cpu;
import gameboj.component.cpu.Cpu.Interrupt;
import gameboj.component.memory.Ram;
import gameboj.component.memory.RamController;

public final class LcdController implements Component, Clocked {

	private Bus bus;
	private final Cpu cpu;
	private long nextNonIdleCycle;

	private int copySource;
	private int copyDestination;

	private LcdImage lcdImage;
	private LcdImage.Builder lcdImageBuilder;
	private final BitVector transparentLine;
	private boolean firstLineDrawn;
	private int winY;

	public final static int LCD_WIDTH = 160;
	public final static int LCD_HEIGHT = 144;
	private final static int CANVAS_SIZE = 256;
	private final static int WIN_OFFSET = 7;

	private final static int LINE_CYCLES = 114;
	private final static int VBLANK_CYCLES = 154;

	private final RamController VRAMController = new RamController(
			new Ram(AddressMap.VIDEO_RAM_SIZE),
			AddressMap.VIDEO_RAM_START,
			AddressMap.VIDEO_RAM_END);

	private final RamController OAMController = new RamController(
			new Ram(AddressMap.OAM_RAM_SIZE),
			AddressMap.OAM_START,
			AddressMap.OAM_END);

	private enum Reg implements Register {
		LCDC, STAT, SCY, SCX, LY, LYC, DMA, BGP, OBP0, OBP1, WY, WX
	}

	private enum LCDCB implements Bit {
		BG, OBJ, OBJ_SIZE, BG_AREA, TILE_SOURCE, WIN, WIN_AREA, LCD_STATUS
	}

	private enum STATB implements Bit {
		MODE0, MODE1, LYC_EQ_LY, INT_MODE0, INT_MODE1, INT_MODE2, INT_LYC
	}

	private enum imageType {
		BG, WIN, SPRITE
	}

	private final RegisterFile<Reg> regFile = new RegisterFile<>(Reg.values());

	public LcdController(Cpu cpu) {
		this.cpu = Objects.requireNonNull(cpu);
		transparentLine = new BitVector(LCD_WIDTH);
	}

	@Override
	public void attachTo(Bus bus) {
		this.bus = Objects.requireNonNull(bus);
		bus.attach(this);
	}

	@Override
	public void cycle(long cycle) {
		if (copyDestination < AddressMap.OAM_END) {
			OAMController.write(copyDestination, bus.read(copySource));
			copyDestination++;
			copySource++;
		}
		if (nextNonIdleCycle == Long.MAX_VALUE && regFile.testBit(Reg.LCDC, LCDCB.LCD_STATUS)) {
			nextNonIdleCycle = cycle;
		} else if (cycle != nextNonIdleCycle)
			return;

		reallyCycle();
	}

	@Override
	public int read(int address) {
		if (inBounds(address, AddressMap.REGS_LCDC_START, AddressMap.REGS_LCDC_END)) {
			return regFile.get(Reg.values()[address - AddressMap.REGS_LCDC_START]);
		} else if (inBounds(address, AddressMap.VIDEO_RAM_START, AddressMap.VIDEO_RAM_END)) {
			return VRAMController.read(address);
		} else if (inBounds(address, AddressMap.OAM_START, AddressMap.OAM_END)) {
			return OAMController.read(address);
		}
		return NO_DATA;
	}

	@Override
	public void write(int address, int data) {
		if (inBounds(address, AddressMap.REGS_LCDC_START, AddressMap.REGS_LCDC_END)) {
			Reg reg = Reg.values()[address - AddressMap.REGS_LCDC_START];
			switch (reg) {
				case LCDC: {
					if (!Bits.test(data, 7)) {
						updateLYorLYC(Reg.LY, 0);
						changeModeSTAT(0);
						nextNonIdleCycle = Long.MAX_VALUE;
					}
					regFile.set(Reg.LCDC, data);
				}
				break;
				case STAT: {
					regFile.set(Reg.STAT, (regFile.get(reg) & 0x07) | (data & 0xF8));
				}
				break;
				case LYC: {
					updateLYorLYC(Reg.LYC, data);
				}
				break;
				case DMA: {
					regFile.set(Reg.DMA, data);
					copyDestination = AddressMap.OAM_START;
					copySource = data << 8;
				}
				default: {
					regFile.set(reg, data);
				}
			}
		} else if (inBounds(address, AddressMap.VIDEO_RAM_START, AddressMap.VIDEO_RAM_END)) {
			VRAMController.write(address, data);
		} else if (inBounds(address, AddressMap.OAM_START, AddressMap.OAM_END)) {
			OAMController.write(address, data);
		}
	}

	public LcdImage currentImage() {
		if (lcdImage == null)
			return new LcdImage.Builder(LCD_WIDTH, LCD_HEIGHT).build();
		return lcdImage;
	}

	private void reallyCycle() {
		if (regFile.get(Reg.LY) == LCD_HEIGHT-1 && checkModeSTAT(0)) {
			changeModeSTAT(1);
			tryLcdStatInterrupt(1);
			cpu.requestInterrupt(Interrupt.VBLANK);
			updateLYorLYC(Reg.LY, regFile.get(Reg.LY) + 1);
			lcdImage = lcdImageBuilder.build();
			firstLineDrawn = false;
			nextNonIdleCycle += LINE_CYCLES;

		} else if (inBounds(regFile.get(Reg.LY), LCD_HEIGHT, VBLANK_CYCLES)) {
			updateLYorLYC(Reg.LY, (regFile.get(Reg.LY) + 1) % VBLANK_CYCLES);
			nextNonIdleCycle += LINE_CYCLES;

		} else if (checkModeSTAT(0) || checkModeSTAT(1)) {
			if (!firstLineDrawn) {
				lcdImageBuilder = new LcdImage.Builder(LCD_WIDTH, LCD_HEIGHT);
				winY = 0;
			} else
				updateLYorLYC(Reg.LY, regFile.get(Reg.LY) + 1);

			changeModeSTAT(2);
			tryLcdStatInterrupt(2);
			nextNonIdleCycle += 20;

		} else if (checkModeSTAT(2)) {
			changeModeSTAT(3);
			if (!firstLineDrawn)
				firstLineDrawn = true;

			LcdImageLine currentLine = computeLine(regFile.get(Reg.LY));

			lcdImageBuilder.setLine(regFile.get(Reg.LY), currentLine);
			nextNonIdleCycle += 43;

		} else if (checkModeSTAT(3)) {
			changeModeSTAT(0);
			tryLcdStatInterrupt(0);
			nextNonIdleCycle += 51;
		}
	}

	private void tryLcdStatInterrupt(int mode) {
		if (regFile.testBit(Reg.STAT, STATB.values()[mode + 3]))
			cpu.requestInterrupt(Interrupt.LCD_STAT);
	}

	private void changeModeSTAT(int mode) {
		regFile.setBit(Reg.STAT, STATB.MODE0, mode % 2 == 1);
		regFile.setBit(Reg.STAT, STATB.MODE1, mode / 2 == 1);
	}

	private boolean checkModeSTAT(int mode) {
		return regFile.testBit(Reg.STAT, STATB.MODE0) == (mode % 2 == 1)
				&& regFile.testBit(Reg.STAT, STATB.MODE1) == (mode / 2 == 1);
	}

	private LcdImageLine computeLine(int row) {
		LcdImageLine newLine = new LcdImageLine.Builder(LCD_WIDTH).build();
		LcdImageLine.Builder lineBGBuilder = new LcdImageLine.Builder(CANVAS_SIZE);
		LcdImageLine.Builder lineWinBuilder = new LcdImageLine.Builder(CANVAS_SIZE);

		int relativeRow = (row + regFile.get(Reg.SCY)) % CANVAS_SIZE;
		int winPos = Math.max(0, regFile.get(Reg.WX) - WIN_OFFSET);

		for (int col = 0; col < 32; col++) {
			int[] BGtile = getVectors(imageType.BG, getTileIndex(imageType.BG, relativeRow, col), relativeRow);
			int[] WinTile = getVectors(imageType.WIN, getTileIndex(imageType.WIN, winY, col), winY);
			lineBGBuilder.setBytes(col, BGtile[0], BGtile[1]);
			lineWinBuilder.setBytes(col, WinTile[0], WinTile[1]);
		}

		LcdImageLine[] spriteLines = getSpriteLines(row);
		LcdImageLine spriteBGLine = spriteLines[0];
		LcdImageLine spriteFGLine = spriteLines[1];
		LcdImageLine WinLine = lineWinBuilder.build();
		LcdImageLine BGLine = lineBGBuilder.build();

		WinLine = WinLine.extractWrapped(0, LCD_WIDTH).shift(-winPos);
		BGLine = BGLine.extractWrapped(regFile.get(Reg.SCX), LCD_WIDTH);

		BitVector opacityBG = transparentLine;

		if (backgroundIsEnabled()) {
			newLine = BGLine.mapColors(regFile.get(Reg.BGP));
			opacityBG = spriteBGLine.opacity().and(BGLine.opacity().not());
		}
		if (spriteIsEnabled())
			newLine = spriteBGLine.below(newLine, opacityBG.not());
		if (windowIsEnabled()) {
			newLine = WinLine.mapColors(regFile.get(Reg.BGP)).join(newLine, LCD_WIDTH - winPos);
			winY++;
		}
		if (spriteIsEnabled())
			newLine = newLine.below(spriteFGLine);
		return newLine;
	}

	private LcdImageLine[] getSpriteLines(int row) {
		int[] sprites = spritesIntersectingLine(row);
		LcdImageLine spriteBGLine = new LcdImageLine.Builder(160).build();
		LcdImageLine spriteFGLine = new LcdImageLine.Builder(160).build();

		for (int number : sprites) {
			LcdImageLine.Builder singleSpriteLineBuilder = new LcdImageLine.Builder(160);
			int spriteYLoc = OAMController.read(AddressMap.OAM_START + number * 4) - 16;
			int spriteXLoc = OAMController.read(AddressMap.OAM_START + number * 4 + 1) - 8;
			int spriteIndex = OAMController.read(AddressMap.OAM_START + number * 4 + 2);
			int infoByte = OAMController.read(AddressMap.OAM_START + number * 4 + 3);
			int spriteSize = regFile.testBit(Reg.LCDC, LCDCB.OBJ_SIZE) ? 16 : 8;
			int spritePal = Bits.test(infoByte, 4) ? regFile.get(Reg.OBP1) : regFile.get(Reg.OBP0);

			int spriteRow = row - spriteYLoc;
			if (Bits.test(infoByte, 6))
				spriteRow = spriteSize - spriteRow - 1;

			if (spriteSize == 16)
				spriteIndex = Bits.set(spriteIndex, 0, false);

			int[] sprite = getVectors(imageType.SPRITE, spriteIndex, spriteRow);

			if (Bits.test(infoByte, 5))
				sprite = new int[] { Bits.reverse8(sprite[0]), Bits.reverse8(sprite[1]) };

			singleSpriteLineBuilder.setBytes(0, sprite[0], sprite[1]);
			LcdImageLine singleSpriteLine = singleSpriteLineBuilder.build().shift(-spriteXLoc).mapColors(spritePal);

			if (Bits.test(infoByte, 7))
				spriteBGLine = spriteBGLine.below(singleSpriteLine);
			else
				spriteFGLine = spriteFGLine.below(singleSpriteLine);
		}
		return new LcdImageLine[] { spriteBGLine, spriteFGLine };
	}

	private int getTileIndex(imageType type, int row, int col) {
		int tileNumber = 32 * (row / 8) + col;

		LCDCB addressArea = type == imageType.BG ? LCDCB.BG_AREA : LCDCB.WIN_AREA;

		int indexStartAddress = regFile.testBit(Reg.LCDC, addressArea) ? AddressMap.BG_DISPLAY_DATA[1]
				: AddressMap.BG_DISPLAY_DATA[0];

		return VRAMController.read(indexStartAddress + tileNumber);
	}

	private int[] getVectors(imageType type, int index, int row) {
		int tileAddress = regFile.testBit(
				Reg.LCDC, LCDCB.TILE_SOURCE) || type == imageType.SPRITE
				? AddressMap.TILE_SOURCE[1] + index * 0x10
				: AddressMap.TILE_SOURCE[0] + (Bits.clip(8, index + 0x80)) * 0x10;

		int size = type != imageType.SPRITE || !regFile.testBit(Reg.LCDC, LCDCB.OBJ_SIZE) ? 8 : 16;

		int lsb = Bits.reverse8(VRAMController.read(tileAddress + 2 * (row % size)));
		int msb = Bits.reverse8(VRAMController.read(tileAddress + 2 * (row % size) + 1));

		return new int[] { msb, lsb };
	}

	private boolean windowIsEnabled() {
		return regFile.get(Reg.WY) <= regFile.get(Reg.LY) && regFile.testBit(Reg.LCDC, LCDCB.WIN)
				&& inBounds(regFile.get(Reg.WX) - WIN_OFFSET, -WIN_OFFSET, LCD_WIDTH);
	}

	private boolean backgroundIsEnabled() {
		return regFile.testBit(Reg.LCDC, LCDCB.BG);
	}

	private boolean spriteIsEnabled() {
		return regFile.testBit(Reg.LCDC, LCDCB.OBJ);
	}

	private int[] spritesIntersectingLine(int row) {
		Integer[] sprites = new Integer[10];
		int spriteNumber = 0;
		int count = 0;

		while (count < 10 && spriteNumber < 40) {
			int tileYLoc = OAMController.read(AddressMap.OAM_START + spriteNumber * 4) - 16;
			int tileXLoc = OAMController.read(AddressMap.OAM_START + spriteNumber * 4 + 1) - 8;
			int spriteSize = regFile.testBit(Reg.LCDC, LCDCB.OBJ_SIZE) ? 16 : 8;

			if (inBounds(row, tileYLoc, tileYLoc + spriteSize)) {
				sprites[count] = (tileXLoc << 8) + spriteNumber;
				count++;
			}
			spriteNumber++;
		}

		Arrays.sort(sprites, 0, count);

		int[] spritesIndex = new int[count];
		for (int i = 0; i < count; i++)
			spritesIndex[i] = Bits.clip(8, sprites[count - 1 - i]);

		return spritesIndex;
	}

	private void updateLYorLYC(Reg reg, int val) {
		regFile.set(reg, val);
		if (regFile.get(Reg.LY) == regFile.get(Reg.LYC)) {
			regFile.setBit(Reg.STAT, STATB.LYC_EQ_LY, true);
			if (regFile.testBit(Reg.STAT, STATB.INT_LYC))
				cpu.requestInterrupt(Interrupt.LCD_STAT);
		} else
			regFile.setBit(Reg.STAT, STATB.LYC_EQ_LY, false);

	}
	
	private boolean inBounds(int val, int lowerBound, int upperBound) {
		return lowerBound <= val && val < upperBound;
	}
}
