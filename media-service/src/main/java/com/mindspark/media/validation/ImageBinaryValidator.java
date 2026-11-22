package com.mindspark.media.validation;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ImageBinaryValidator {

    private static final int MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final int MAX_DIMENSION = 4096;

    public record ValidatedImage(
            byte[] sanitizedBytes,
            String contentType,
            String extension,
            int width,
            int height
    ) {}

    public ValidatedImage validateAndSanitize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EMPTY_FILE");
        }

        if (bytes.length > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
        }

        // Fast text inspection for SVG / HTML / Script injection
        checkForbiddenTextPayloads(bytes);

        // Explicit rejection of known unsupported formats (GIF, PDF, Executables, etc.)
        checkForExplicitlyForbiddenFormats(bytes);

        // Detect supported formats by raw magic bytes
        if (isPng(bytes)) {
            return processPng(bytes);
        } else if (isJpeg(bytes)) {
            return processJpeg(bytes);
        } else if (isWebp(bytes)) {
            return processWebp(bytes);
        } else {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE");
        }
    }

    private void checkForbiddenTextPayloads(byte[] bytes) {
        int checkLen = Math.min(bytes.length, 2048);
        String prefix = new String(bytes, 0, checkLen, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
        if (prefix.contains("<svg") || prefix.contains("<?xml") || prefix.contains("<!doctype html")
                || prefix.contains("<html") || prefix.contains("<script") || prefix.contains("xmlns=")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE");
        }
    }

    private void checkForExplicitlyForbiddenFormats(byte[] bytes) {
        // GIF: GIF87a or GIF89a
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8'
                && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a') {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE");
        }
        // PDF: %PDF
        if (bytes.length >= 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F') {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE");
        }
        // DOS/Windows executable: MZ
        if (bytes.length >= 2 && bytes[0] == 'M' && bytes[1] == 'Z') {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE");
        }
    }

    private boolean isPng(byte[] bytes) {
        if (bytes.length < 8) return false;
        return (bytes[0] == (byte) 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A);
    }

    private boolean isJpeg(byte[] bytes) {
        if (bytes.length < 3) return false;
        return (bytes[0] == (byte) 0xFF
                && bytes[1] == (byte) 0xD8
                && bytes[2] == (byte) 0xFF);
    }

    private boolean isWebp(byte[] bytes) {
        if (bytes.length < 12) return false;
        return (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P');
    }

    private ValidatedImage processPng(byte[] bytes) {
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
        }

        if (img == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
        }

        int width = img.getWidth();
        int height = img.getHeight();

        if (width <= 0 || height <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
        }

        if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IMAGE_DIMENSIONS_EXCEEDED");
        }

        // Re-encode to clean PNG, stripping EXIF, GPS, and polyglots
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            boolean written = ImageIO.write(img, "png", baos);
            if (!written) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
        }

        byte[] cleanBytes = baos.toByteArray();
        return new ValidatedImage(cleanBytes, "image/png", "png", width, height);
    }

    private ValidatedImage processJpeg(byte[] bytes) {
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
        }

        if (img == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
        }

        int width = img.getWidth();
        int height = img.getHeight();

        if (width <= 0 || height <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
        }

        if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IMAGE_DIMENSIONS_EXCEEDED");
        }

        // Convert to RGB BufferedImage (handles transparent PNG/polyglots safely before JPEG encoding)
        BufferedImage rgbImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = rgbImage.createGraphics();
        try {
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, width, height);
            g2d.drawImage(img, 0, 0, null);
        } finally {
            g2d.dispose();
        }

        // Re-encode to clean JPEG, stripping EXIF/GPS/comments metadata
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            boolean written = ImageIO.write(rgbImage, "jpeg", baos);
            if (!written) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
        }

        byte[] cleanBytes = baos.toByteArray();
        return new ValidatedImage(cleanBytes, "image/jpeg", "jpg", width, height);
    }

    private ValidatedImage processWebp(byte[] bytes) {
        if (bytes.length < 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
        }

        // Validate RIFF size
        long riffSize = (bytes[4] & 0xFFL) | ((bytes[5] & 0xFFL) << 8)
                | ((bytes[6] & 0xFFL) << 16) | ((bytes[7] & 0xFFL) << 24);
        if (riffSize + 8 > bytes.length + 1024) { // allow modest padding
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
        }

        String chunkType = new String(bytes, 12, 4, StandardCharsets.US_ASCII);
        int width;
        int height;

        try {
            if ("VP8 ".equals(chunkType)) {
                // Lossy VP8
                if (bytes.length < 30) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
                }
                // Check keyframe start code: 0x9D 0x01 0x2A at byte 23..25
                if ((bytes[23] & 0xFF) != 0x9D || (bytes[24] & 0xFF) != 0x01 || (bytes[25] & 0xFF) != 0x2A) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
                }
                width = (bytes[26] & 0xFF) | ((bytes[27] & 0x3F) << 8);
                height = (bytes[28] & 0xFF) | ((bytes[29] & 0x3F) << 8);
            } else if ("VP8L".equals(chunkType)) {
                // Lossless VP8L
                if (bytes.length < 25) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
                }
                if (bytes[20] != 0x2F) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
                }
                int b0 = bytes[21] & 0xFF;
                int b1 = bytes[22] & 0xFF;
                int b2 = bytes[23] & 0xFF;
                int b3 = bytes[24] & 0xFF;
                int val = b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
                width = 1 + (val & 0x3FFF);
                height = 1 + ((val >> 14) & 0x3FFF);
            } else if ("VP8X".equals(chunkType)) {
                // Extended VP8X
                if (bytes.length < 30) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
                }
                width = 1 + ((bytes[24] & 0xFF) | ((bytes[25] & 0xFF) << 8) | ((bytes[26] & 0xFF) << 16));
                height = 1 + ((bytes[27] & 0xFF) | ((bytes[28] & 0xFF) << 8) | ((bytes[29] & 0xFF) << 16));
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
        }

        if (width <= 0 || height <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
        }

        if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IMAGE_DIMENSIONS_EXCEEDED");
        }

        return new ValidatedImage(bytes, "image/webp", "webp", width, height);
    }
}
