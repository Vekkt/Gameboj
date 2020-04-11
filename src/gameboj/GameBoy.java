package gameboj;

import gameboj.component.Joypad;
import gameboj.component.Timer;
import gameboj.component.cartridge.Cartridge;
import gameboj.component.cpu.Cpu;
import gameboj.component.lcd.LcdController;
import gameboj.component.memory.BootRomController;
import gameboj.component.memory.Ram;
import gameboj.component.memory.RamController;
import gameboj.component.sound.SoundController;
import gameboj.component.sound.SoundOutput;

import java.util.Objects;

/**
 * Represents the GameBoy system, with all its components attached
 * 
 * @author Francois BURGUET 288683
 * @author Gaietan Renault 283350
 */

public final class GameBoy {

	private final Cartridge rom;
	private final Bus bus;
	private final Cpu cpu;
	private final Timer timer;
	private final Joypad joypad;
	private final LcdController lcd;
	private final SoundController apu;
	private final SoundOutput output;
	private int cycle;

	public static final long CLOCK_FREQ = (long) Math.pow(2, 20);
	public static final double CLOCK_NANO_FREQ = CLOCK_FREQ / 1e9;

	/**
	 * Initialize the GameBoy and all its components
	 * 
	 * @param cartridge : a virtual game cartridge, non-null
	 * @throws NullPointerException if cartridge is null
	 */
	public GameBoy(Cartridge cartridge) {
		rom = Objects.requireNonNull(cartridge);

		output = new SoundOutput();

		bus = new Bus();
		cpu = new Cpu();
		timer = new Timer(cpu);
		joypad = new Joypad(cpu);
		lcd = new LcdController(cpu);
		apu = new SoundController(output);

		Ram workRam = new Ram(AddressMap.WORK_RAM_SIZE);
		bus.attach(new RamController(workRam, AddressMap.WORK_RAM_START, AddressMap.WORK_RAM_END));
		bus.attach(new RamController(workRam, AddressMap.ECHO_RAM_START, AddressMap.ECHO_RAM_END));

		bus.attach(new BootRomController(rom));
		bus.attach(timer);
		bus.attach(joypad);
		cpu.attachTo(bus);
		lcd.attachTo(bus);
		apu.attachTo(bus);

		output.start();
	}

	public Cartridge getRom() {
		return this.rom;
	}

	/**
	 * Returns the joypad attached to the GB
	 * 
	 * @return joypad : the joypad of the GB
	 */
	public Joypad joypad() {
		return joypad;
	}

	/**
	 * Returns the CPU attached to the bus of the GB
	 * 
	 * @return cpu : the cpu of the GB
	 */
	public Cpu cpu() {
		return cpu;
	}

	/**
	 * Returns the bus of the GB
	 * 
	 * @return bus : the bus of the GB
	 */
	public Bus bus() {
		return bus;
	}

	/**
	 * Returns the current cycle
	 * 
	 * @return cycle : the current cycle
	 */
	public long cycles() {
		return cycle;
	}

	/**
	 * Returns the timer attached to the bus of the GB
	 * 
	 * @return timer : the timer of the GB
	 */
	public Timer timer() {
		return timer;
	}

	/**
	 * Returns the LCD controller attached to the bus of the GB
	 * 
	 * @return timer : the LCD controller of the GB
	 */
	public LcdController lcdController() {
		return lcd;
	}

	/**
	 * Runs the GB until the specified cycle
	 * 
	 * @param cycle : cycle to stop at the simulation
	 * @throws IllegalArgumentException if invalid cycle
	 */
	public void runUntil(long cycle) {
		Preconditions.checkArgument(0 <= cycle && this.cycle <= cycle);
		long current = cycles();
		for (long i = current; i < cycle; i++) {
			timer.cycle(this.cycle);
			lcd.cycle(this.cycle);
			cpu.cycle(this.cycle);
			apu.cycle(this.cycle);
			++this.cycle;
		}
	}
}
