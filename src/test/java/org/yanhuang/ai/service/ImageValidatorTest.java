package org.yanhuang.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yanhuang.ai.model.AnthropicMessage;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ImageValidator service.
 */
@DisplayName("ImageValidator Tests")
class ImageValidatorTest {

    private ImageValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ImageValidator();
    }

    @Test
    @DisplayName("Valid base64 image should pass validation")
    void testValidBase64Image() {
        // Create a small valid base64 image (1x1 pixel PNG)
        String smallPng = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

        AnthropicMessage.ImageSource imageSource = new AnthropicMessage.ImageSource();
        imageSource.setType("base64");
        imageSource.setMediaType("image/png");
        imageSource.setData(smallPng);

        assertDoesNotThrow(() -> validator.validateImageSource(imageSource));
    }

    @Test
    @DisplayName("Valid URL image should pass validation")
    void testValidUrlImage() {
        AnthropicMessage.ImageSource imageSource = new AnthropicMessage.ImageSource();
        imageSource.setType("url");
        imageSource.setMediaType("image/jpeg");
        imageSource.setData("https://example.com/image.jpg");

        assertDoesNotThrow(() -> validator.validateImageSource(imageSource));
    }

    @Test
    @DisplayName("Null image source should fail validation")
    void testNullImageSource() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validator.validateImageSource(null)
        );
        assertTrue(exception.getMessage().contains("cannot be null"));
    }

    @Test
    @DisplayName("Missing type field should fail validation")
    void testMissingType() {
        AnthropicMessage.ImageSource imageSource = new AnthropicMessage.ImageSource();
        imageSource.setMediaType("image/png");
        imageSource.setData("data");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validator.validateImageSource(imageSource)
        );
        assertTrue(exception.getMessage().contains("type is required"));
    }

    @Test
    @DisplayName("Unsupported type should fail validation")
    void testUnsupportedType() {
        AnthropicMessage.ImageSource imageSource = new AnthropicMessage.ImageSource();
        imageSource.setType("invalid_type");
        imageSource.setMediaType("image/png");
        imageSource.setData("data");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validator.validateImageSource(imageSource)
        );
        assertTrue(exception.getMessage().contains("Unsupported image source type"));
    }

    @Test
    @DisplayName("Missing media_type field should fail validation")
    void testMissingMediaType() {
        AnthropicMessage.ImageSource imageSource = new AnthropicMessage.ImageSource();
        imageSource.setType("base64");
        imageSource.setData("data");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validator.validateImageSource(imageSource)
        );
        assertTrue(exception.getMessage().contains("media_type is required"));
    }

    @Test
    @DisplayName("Unsupported media type should fail validation")
    void testUnsupportedMediaType() {
        AnthropicMessage.ImageSource imageSource = new AnthropicMessage.ImageSource();
        imageSource.setType("base64");
        imageSource.setMediaType("image/bmp");  // BMP not supported
        imageSource.setData("data");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validator.validateImageSource(imageSource)
        );
        assertTrue(exception.getMessage().contains("Unsupported image media type"));
    }

    @Test
    @DisplayName("All supported media types should be valid")
    void testAllSupportedMediaTypes() {
        String[] supportedTypes = {"image/jpeg", "image/png", "image/gif", "image/webp"};

        for (String mediaType : supportedTypes) {
            AnthropicMessage.ImageSource imageSource = new AnthropicMessage.ImageSource();
            imageSource.setType("url");
            imageSource.setMediaType(mediaType);
            imageSource.setData("https://example.com/image.jpg");

            assertDoesNotThrow(() -> validator.validateImageSource(imageSource),
                "Media type " + mediaType + " should be supported");
        }
    }

    @Test
    @DisplayName("Missing data field should fail validation")
    void testMissingData() {
        AnthropicMessage.ImageSource imageSource = new AnthropicMessage.ImageSource();
        imageSource.setType("base64");
        imageSource.setMediaType("image/png");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validator.validateImageSource(imageSource)
        );
        assertTrue(exception.getMessage().contains("data is required"));
    }

    @Test
    @DisplayName("Invalid base64 encoding should fail validation")
    void testInvalidBase64() {
        AnthropicMessage.ImageSource imageSource = new AnthropicMessage.ImageSource();
        imageSource.setType("base64");
        imageSource.setMediaType("image/png");
        imageSource.setData("not-valid-base64!!!");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validator.validateImageSource(imageSource)
        );
        assertTrue(exception.getMessage().contains("Invalid base64 encoding"));
    }

    @Test
    @DisplayName("Oversized base64 image should fail validation")
    void testOversizedBase64Image() {
        // Create a base64 string larger than 5MB
        byte[] largeData = new byte[6 * 1024 * 1024];  // 6MB
        String largeBase64 = Base64.getEncoder().encodeToString(largeData);

        AnthropicMessage.ImageSource imageSource = new AnthropicMessage.ImageSource();
        imageSource.setType("base64");
        imageSource.setMediaType("image/png");
        imageSource.setData(largeBase64);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validator.validateImageSource(imageSource)
        );
        assertTrue(exception.getMessage().contains("exceeds maximum limit"));
    }

    @Test
    @DisplayName("Invalid URL format should fail validation")
    void testInvalidUrlFormat() {
        AnthropicMessage.ImageSource imageSource = new AnthropicMessage.ImageSource();
        imageSource.setType("url");
        imageSource.setMediaType("image/jpeg");
        imageSource.setData("not-a-valid-url");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validator.validateImageSource(imageSource)
        );
        assertTrue(exception.getMessage().contains("Invalid image URL format"));
    }

    @Test
    @DisplayName("URL with whitespace should fail validation")
    void testUrlWithWhitespace() {
        AnthropicMessage.ImageSource imageSource = new AnthropicMessage.ImageSource();
        imageSource.setType("url");
        imageSource.setMediaType("image/jpeg");
        imageSource.setData("https://example.com/image with spaces.jpg");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validator.validateImageSource(imageSource)
        );
        assertTrue(exception.getMessage().contains("invalid whitespace"));
    }

    @Test
    @DisplayName("Valid HTTPS URL should pass validation")
    void testValidHttpsUrl() {
        AnthropicMessage.ImageSource imageSource = new AnthropicMessage.ImageSource();
        imageSource.setType("url");
        imageSource.setMediaType("image/png");
        imageSource.setData("https://cdn.example.com/images/photo.png");

        assertDoesNotThrow(() -> validator.validateImageSource(imageSource));
    }

    @Test
    @DisplayName("Valid HTTP URL should pass validation")
    void testValidHttpUrl() {
        AnthropicMessage.ImageSource imageSource = new AnthropicMessage.ImageSource();
        imageSource.setType("url");
        imageSource.setMediaType("image/webp");
        imageSource.setData("http://example.com/image.webp");

        assertDoesNotThrow(() -> validator.validateImageSource(imageSource));
    }
}
