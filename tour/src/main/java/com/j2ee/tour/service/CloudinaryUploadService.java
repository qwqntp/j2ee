package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.dto.response.ImageUploadResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
public class CloudinaryUploadService {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    // ponytail: fixed destination; add app.cloudinary.folder when multiple upload folders are needed.
    private static final String FOLDER = "tour-images";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final String CRLF = "\r\n";

    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public CloudinaryUploadService(
            @Value("${app.cloudinary.cloud-name:}") String cloudName,
            @Value("${app.cloudinary.api-key:}") String apiKey,
            @Value("${app.cloudinary.api-secret:}") String apiSecret,
            ObjectMapper json) {
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.json = json;
    }

    public ImageUploadResponse upload(MultipartFile file) {
        requireConfigured();
        if (file == null || file.isEmpty() || file.getContentType() == null
                || !ALLOWED_TYPES.contains(file.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Only JPEG, PNG, WebP and GIF images up to 5 MB are accepted");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image must be 5 MB or smaller");
        }

        long timestamp = Instant.now().getEpochSecond();
        String toSign = "folder=" + FOLDER + "&timestamp=" + timestamp;
        String signature = sign(toSign + apiSecret);
        String boundary = "----tour-upload-" + UUID.randomUUID().toString().replace("-", "");

        try {
            byte[] body = multipartBody(boundary, file, timestamp, signature);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.cloudinary.com/v1_1/"
                            + cloudName + "/image/upload"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Image upload failed");
            }
            JsonNode root = json.readTree(response.body());
            JsonNode url = root == null ? null : root.get("secure_url");
            JsonNode publicId = root == null ? null : root.get("public_id");
            if (url == null || !url.isString() || publicId == null || !publicId.isString()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Image upload returned an invalid response");
            }
            return new ImageUploadResponse(url.stringValue(), publicId.stringValue());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Image upload failed");
        } catch (JacksonException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Image upload returned an invalid response");
        }
    }

    private void requireConfigured() {
        if (isBlank(cloudName) || isBlank(apiKey) || isBlank(apiSecret)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Image upload is not configured");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String sign(String payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 unavailable", exception);
        }
    }

    private byte[] multipartBody(String boundary, MultipartFile file,
                                 long timestamp, String signature) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        startPart(body, boundary);
        body.writeBytes(("Content-Disposition: form-data; name=\"file\"; filename=\"upload\"" + CRLF
                + "Content-Type: " + file.getContentType() + CRLF + CRLF)
                .getBytes(StandardCharsets.UTF_8));
        body.writeBytes(file.getBytes());
        body.writeBytes(CRLF.getBytes(StandardCharsets.UTF_8));
        formField(body, boundary, "api_key", apiKey);
        formField(body, boundary, "timestamp", Long.toString(timestamp));
        formField(body, boundary, "folder", FOLDER);
        formField(body, boundary, "signature", signature);
        body.writeBytes(("--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }

    private static void formField(ByteArrayOutputStream body, String boundary,
                                  String name, String value) {
        startPart(body, boundary);
        body.writeBytes(("Content-Disposition: form-data; name=\"" + name + "\"" + CRLF + CRLF
                + value + CRLF).getBytes(StandardCharsets.UTF_8));
    }

    private static void startPart(ByteArrayOutputStream body, String boundary) {
        body.writeBytes(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
    }
}
