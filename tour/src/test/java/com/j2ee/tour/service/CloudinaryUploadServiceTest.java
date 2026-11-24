package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudinaryUploadServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void uploadFailsClosedWithoutConfig() {
        var service = new CloudinaryUploadService("", "", "", objectMapper);

        var ex = assertThrows(ApiException.class, () -> service.upload(pngFile()));
        assertEquals(503, ex.getStatus().value());
    }

    @Test
    void uploadRejectsOversizedFile() {
        var service = new CloudinaryUploadService("cloud", "key", "secret", objectMapper);
        var big = new MockMultipartFile("file", "big.png", "image/png", new byte[6 * 1024 * 1024]);

        var ex = assertThrows(ApiException.class, () -> service.upload(big));

        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void uploadRejectsNonImage() {
        var service = new CloudinaryUploadService("cloud", "key", "secret", objectMapper);
        var text = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        var ex = assertThrows(ApiException.class, () -> service.upload(text));

        assertEquals(400, ex.getStatus().value());
    }

    private static MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "tiny.png", "image/png", new byte[] {1, 2, 3});
    }
}
