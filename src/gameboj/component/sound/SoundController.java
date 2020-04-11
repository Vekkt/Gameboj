package gameboj.component.sound;

import gameboj.Register;
import gameboj.RegisterFile;
import gameboj.component.Clocked;
import gameboj.component.Component;

import javax.sound.sampled.SourceDataLine;
import javax.xml.transform.Source;

import static gameboj.AddressMap.*;
import static gameboj.Preconditions.*;
import static gameboj.GameBoy.CLOCK_FREQ;
import static gameboj.component.sound.SoundOutput.SAMPLE_RATE;
import static java.util.Objects.requireNonNull;

public final class SoundController implements Component, Clocked {
    public static final int BUFFER_SIZE = 2048;
    public static final int SEQUENCER_FREQUENCY = 512;
    public static final int SEQUENCER_COUNTDOWN = (int) (CLOCK_FREQ / SEQUENCER_FREQUENCY);
    public static final int SAMPLE_COUNTDOWN = (int) (CLOCK_FREQ / SAMPLE_RATE);

    protected enum Reg implements Register { VIN, OUTPUT, STATUS }
    protected final RegisterFile<Reg> regFile = new RegisterFile<>(Reg.values());

    private SquareChannel square1;
    private SquareChannel square2;
    private Wave waveChannel;
    private Noise noiseChannel;

    private SourceDataLine outputLine;

    private long frameSequenceCountDown = SEQUENCER_COUNTDOWN;
    private int sampleCountDown = SAMPLE_COUNTDOWN;
    private int frameSequencer = 0;
    private int sampleCount = 0;

    private byte[] soundBuffer;


    public SoundController(SoundOutput output) {
        outputLine = requireNonNull(output).getLine();

        square1 = new SquareA();
        square2 = new SquareB();
        waveChannel = new Wave();
        noiseChannel = new Noise();

        soundBuffer = new byte[BUFFER_SIZE];
    }


    @Override
    public void cycle(long cycle) {
        if (--frameSequenceCountDown == 0) {
            frameSequenceCountDown = SEQUENCER_COUNTDOWN;
            square1.cycle(frameSequencer);
            square2.cycle(frameSequencer);
            waveChannel.cycle(frameSequencer);
            noiseChannel.cycle(frameSequencer);

            frameSequencer = (frameSequencer + 1) % 8;
        }

        square1.step();
        square2.step();
        waveChannel.step();
        noiseChannel.step();

        sampleCountDown--;
        if (sampleCountDown == 0) {
            soundBuffer[sampleCount] = mixLeft();
            soundBuffer[sampleCount + 1] = mixRight();

//            if (soundBuffer[sampleCount] * soundBuffer[sampleCount+1] != 0)
//                System.out.println(String.format("%d       %d", soundBuffer[sampleCount], soundBuffer[sampleCount+1]));

            sampleCount += 2;
            sampleCountDown = SAMPLE_COUNTDOWN;
        }
        if(sampleCount >= BUFFER_SIZE / 2) {
            outputLine.write(soundBuffer, 0, BUFFER_SIZE / 2);
            sampleCount = 0;
        }
    }

    private byte mixLeft() {
        byte buffer = 0;
        int volume = getLeftVolume(); // between 0 and 15
        boolean[] enables = getEnablesLeft();
        if (enables[0]) buffer += square1.getOutputVolume();
        if (enables[1]) buffer += square2.getOutputVolume();
        if (enables[2]) buffer += waveChannel.getOutputVolume();
        if (enables[3]) buffer += noiseChannel.getOutputVolume();

        buffer /= 4;
        buffer *= volume;
        return buffer;
    }

    private byte mixRight() {
        byte buffer = 0;
        int volume = getRightVolume(); // between 0 and 15
        boolean[] enables = getEnablesRight();
        if (enables[0]) buffer += square1.getOutputVolume();
        if (enables[1]) buffer += square2.getOutputVolume();
        if (enables[2]) buffer += waveChannel.getOutputVolume();
        if (enables[3]) buffer += noiseChannel.getOutputVolume();

        buffer /= 4;
        buffer *= volume;
        return buffer;
    }

    private boolean[] getEnablesLeft() {
        boolean[] enables = new boolean[4];
        int chan = (regFile.get(Reg.OUTPUT) >> 4) & 0xF;
        for (int i = 0; i < 4; i++) {
            enables[i] = (chan & 0x1) == 1;
            chan >>= 1;
        }
        return enables;
    }

    private boolean[] getEnablesRight() {
        boolean[] enables = new boolean[4];
        int chan = regFile.get(Reg.OUTPUT) & 0xF;
        for (int i = 0; i < 4; i++) {
            enables[i] = (chan & 0x1) == 1;
            chan >>= 1;
        }
        return enables;
    }

    private int getLeftVolume() {
        return (regFile.get(Reg.VIN) >> 4) & 0x7;
    }

    private int getRightVolume() {
        return regFile.get(Reg.VIN) & 0x7;
    }

    @Override
    public int read(int address) {
        checkBits16(address);
        int data;
        if (inBounds(address, REGS_CH1_START, REGS_CH1_END)) {
            data = square1.read(address);
            System.out.println(String.format("Reading from SQ1 @0x%x = 0x%x", address, data));
            return data;
        }
        else if (inBounds(address, REGS_CH2_START, REGS_CH2_END)) {
            data = square2.read(address);
            System.out.println(String.format("Reading from SQ2 @0x%x = 0x%x", address, data));
            return data;
        }
        else if (inBounds(address, REGS_CH3_START, REGS_CH3_END)
                || inBounds(address, REG_WAVE_TAB_START, REG_WAVE_TAB_END)) {
            data = waveChannel.read(address);
            System.out.println(String.format("Reading from WAVE @0x%x = 0x%x", address, data));
            return data;
        }
        else if (inBounds(address, REGS_CH4_START, REGS_CH4_END)) {
            data = noiseChannel.read(address);
            System.out.println(String.format("Reading from NOISE @0x%x = 0x%x", address, data));
            return data;
        }
        else if (address == REG_VIN_CONTROL) {
            data = regFile.get(Reg.VIN);
            System.out.println(String.format("Reading from VIN @0x%x = 0x%x", address, data));
            return data;
        }
        else if (address == REG_OUTPUT_CONTROL) {
            data = regFile.get(Reg.OUTPUT);
            System.out.println(String.format("Reading from OUTPUT @0x%x = 0x%x", address, data));
            return data;
        }
        else if (address == REG_STATUS) {
            data = (regFile.get(Reg.STATUS) | 0x70);
            System.out.println(String.format("Reading from STATUS @0x%x = 0x%x", address, data));
            return data;
        }
        else if (inBounds(address, 0xFF27, 0xFF2F)) {
            data = 0xFF;
            System.out.println(String.format("Reading from UNUSED @0x%x = 0x%x", address, data));
            return data;
        }
        return NO_DATA;
    }

    @Override
    public void write(int address, int data) {
        checkBits8(data);
        checkBits16(address);
        if (inBounds(address, REGS_CH1_START, REGS_CH1_END))
            square1.write(address, data);
        else if (inBounds(address, REGS_CH2_START, REGS_CH2_END))
            square2.write(address, data);
        else if (inBounds(address, REGS_CH3_START, REGS_CH3_END)
                || inBounds(address, REG_WAVE_TAB_START, REG_WAVE_TAB_END))
            waveChannel.write(address, data);
        else if (inBounds(address, REGS_CH4_START, REGS_CH4_END))
            noiseChannel.write(address, data);
        else if (address == REG_VIN_CONTROL) regFile.set(Reg.VIN, data);
        else if (address == REG_OUTPUT_CONTROL) regFile.set(Reg.OUTPUT, data);
        else if (address == REG_STATUS)  regFile.set(Reg.STATUS, data);
    }
}
