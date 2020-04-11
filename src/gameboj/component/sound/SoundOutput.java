package gameboj.component.sound;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import static gameboj.component.sound.SoundController.BUFFER_SIZE;

public final class SoundOutput {
    public static final int SAMPLE_RATE = 2048;
    public static final int SAMPLE_SIZE = 8;
    public static final int CHANNELS = 2;
    public static final int FRAME_SIZE = 2;

    private SourceDataLine line;

    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_UNSIGNED,
            SAMPLE_RATE,
            SAMPLE_SIZE,
            CHANNELS,
            FRAME_SIZE,
            SAMPLE_RATE,
            false);

    public SoundOutput() {
        try {
            line = AudioSystem.getSourceDataLine(AUDIO_FORMAT);
            line.open(AUDIO_FORMAT, BUFFER_SIZE);
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }
    }

    public void start() {
        line.start();
    }

    public void stop() {
        line.drain();
        line.stop();
        line = null;
    }

    public SourceDataLine getLine() {
        return line;
    }


}
