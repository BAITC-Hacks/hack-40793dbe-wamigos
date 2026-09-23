package kz.hackalem.wamigos;

public final class TestMediaFixtures {

    private static final int MP3_FRAME_LENGTH = 417;
    private static final int MP3_FRAME_COUNT = 4;

    private TestMediaFixtures() {
    }

    public static byte[] mp3() {
        byte[] audio = new byte[MP3_FRAME_LENGTH * MP3_FRAME_COUNT];
        for (int frame = 0; frame < MP3_FRAME_COUNT; frame++) {
            int offset = frame * MP3_FRAME_LENGTH;
            audio[offset] = (byte) 0xff;
            audio[offset + 1] = (byte) 0xfb;
            audio[offset + 2] = (byte) 0x90;
            audio[offset + 3] = (byte) 0x64;
        }
        return audio;
    }
}
