package com.knowitai.validation;

import com.knowitai.exception.InvalidImageException;
import com.knowitai.exception.InvalidImageReason;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Decides whether an upload is really a JPEG, PNG or WebP image.
 * It checks the file's first bytes ("magic bytes"), because a file's name
 * or the Content-Type header can be faked by whoever sends the request.
 */
@Component
public class ImageValidator {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};

    private final long maxBytes;

    public ImageValidator(@Value("${knowit.upload.max-bytes:5242880}") long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImageException(InvalidImageReason.EMPTY_FILE,
                    "No image was provided, or the file is empty.");
        }
        if (file.getSize() > maxBytes) {
            throw tooLarge();
        }
        try {
            return validate(file.getBytes());
        } catch (IOException e) {
            throw new InvalidImageException(InvalidImageReason.UNREADABLE,
                    "The uploaded file could not be read.", e);
        }
    }

    public ValidatedImage validate(byte[] data) {
        if (data == null || data.length == 0) {
            throw new InvalidImageException(InvalidImageReason.EMPTY_FILE,
                    "No image was provided, or the file is empty.");
        }
        if (data.length > maxBytes) {
            throw tooLarge();
        }
        String mimeType = detectMimeType(data);
        if (mimeType == null) {
            throw new InvalidImageException(InvalidImageReason.UNSUPPORTED_TYPE,
                    "Unsupported image type. Please upload a JPEG, PNG or WebP image.");
        }
        return new ValidatedImage(data, mimeType);
    }

    private InvalidImageException tooLarge() {
        return new InvalidImageException(InvalidImageReason.FILE_TOO_LARGE,
                "The image is larger than the maximum allowed size.");
    }

    private static String detectMimeType(byte[] d) {
        if (matchesAt(d, 0, JPEG)) {
            return "image/jpeg";
        }
        if (matchesAt(d, 0, PNG)) {
            return "image/png";
        }
        // WebP files are "RIFF" + 4 size bytes + "WEBP". Other RIFF files (like WAV audio) differ at byte 8.
        if (matchesAt(d, 0, RIFF) && matchesAt(d, 8, WEBP)) {
            return "image/webp";
        }
        return null;
    }

    private static boolean matchesAt(byte[] data, int offset, byte[] signature) {
        if (data.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (data[offset + i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
