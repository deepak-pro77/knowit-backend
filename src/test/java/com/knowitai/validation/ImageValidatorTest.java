package com.knowitai.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.knowitai.exception.InvalidImageException;
import com.knowitai.exception.InvalidImageReason;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ImageValidatorTest {

    private final ImageValidator validator = new ImageValidator(1024);

    /** Builds a fake file: the given starting bytes, padded with zeros up to 'length'. */
    private static byte[] fileStartingWith(int length, int... header) {
        byte[] data = new byte[length];
        for (int i = 0; i < header.length; i++) {
            data[i] = (byte) header[i];
        }
        return data;
    }

    private InvalidImageReason reasonFor(byte[] data) {
        return assertThrows(InvalidImageException.class, () -> validator.validate(data)).getReason();
    }

    @Test
    void acceptsJpeg() {
        ValidatedImage image = validator.validate(fileStartingWith(100, 0xFF, 0xD8, 0xFF, 0xE0));
        assertEquals("image/jpeg", image.mimeType());
    }

    @Test
    void acceptsPng() {
        ValidatedImage image = validator.validate(
                fileStartingWith(100, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A));
        assertEquals("image/png", image.mimeType());
    }

    @Test
    void acceptsWebp() {
        ValidatedImage image = validator.validate(
                fileStartingWith(100, 'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'));
        assertEquals("image/webp", image.mimeType());
    }

    @Test
    void rejectsEmptyFile() {
        assertEquals(InvalidImageReason.EMPTY_FILE, reasonFor(new byte[0]));
    }

    @Test
    void rejectsOversizedFile() {
        assertEquals(InvalidImageReason.FILE_TOO_LARGE,
                reasonFor(fileStartingWith(2000, 0xFF, 0xD8, 0xFF)));
    }

    @Test
    void rejectsTextFilePretendingToBeAnImage() {
        byte[] text = "this is not an image, whatever the file name says".getBytes(StandardCharsets.UTF_8);
        assertEquals(InvalidImageReason.UNSUPPORTED_TYPE, reasonFor(text));
    }

    @Test
    void rejectsGif() {
        byte[] gif = fileStartingWith(100, 'G', 'I', 'F', '8', '9', 'a');
        assertEquals(InvalidImageReason.UNSUPPORTED_TYPE, reasonFor(gif));
    }

    @Test
    void rejectsRiffFileThatIsNotWebp() {
        byte[] wav = fileStartingWith(100, 'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E');
        assertEquals(InvalidImageReason.UNSUPPORTED_TYPE, reasonFor(wav));
    }

    @Test
    void rejectsFileTooShortToIdentify() {
        assertEquals(InvalidImageReason.UNSUPPORTED_TYPE, reasonFor(new byte[]{(byte) 0xFF, (byte) 0xD8}));
    }
}
