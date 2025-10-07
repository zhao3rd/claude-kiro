package org.yanhuang.ai.service;

import org.springframework.stereotype.Component;
import org.yanhuang.ai.model.AnthropicMessage;

import java.util.Base64;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validator for image content blocks.
 * Validates image formats, sizes, and encoding according to Anthropic API specifications.
 */
@Component
public class ImageValidator {

    // Supported image formats according to Anthropic API
    private static final Set<String> SUPPORTED_MEDIA_TYPES = Set.of(
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp"
    );

    // Supported image source types
    private static final Set<String> SUPPORTED_SOURCE_TYPES = Set.of(
        "base64",
        "url"
    );

    // Maximum base64 image size: ~5MB (approximately 32MB unencoded)
    // Base64 encoding increases size by ~33%, so 5MB base64 ≈ 3.75MB original
    private static final int MAX_BASE64_SIZE_BYTES = 5 * 1024 * 1024;

    // URL pattern for validation
    private static final Pattern URL_PATTERN = Pattern.compile(
        "^https?://[^\\s/$.?#].[^\\s]*$",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Validate an image source according to Anthropic API specifications.
     *
     * @param imageSource Image source to validate
     * @throws IllegalArgumentException if image source is invalid
     */
    public void validateImageSource(AnthropicMessage.ImageSource imageSource) {
        if (imageSource == null) {
            throw new IllegalArgumentException("Image source cannot be null");
        }

        // Validate type field
        if (imageSource.getType() == null || imageSource.getType().trim().isEmpty()) {
            throw new IllegalArgumentException("Image source type is required");
        }

        if (!SUPPORTED_SOURCE_TYPES.contains(imageSource.getType())) {
            throw new IllegalArgumentException(
                String.format(
                    "Unsupported image source type: %s. Supported types: %s",
                    imageSource.getType(),
                    SUPPORTED_SOURCE_TYPES
                )
            );
        }

        // Validate media_type field
        if (imageSource.getMediaType() == null || imageSource.getMediaType().trim().isEmpty()) {
            throw new IllegalArgumentException("Image media_type is required");
        }

        if (!SUPPORTED_MEDIA_TYPES.contains(imageSource.getMediaType())) {
            throw new IllegalArgumentException(
                String.format(
                    "Unsupported image media type: %s. Supported types: %s",
                    imageSource.getMediaType(),
                    SUPPORTED_MEDIA_TYPES
                )
            );
        }

        // Validate data field
        if (imageSource.getData() == null || imageSource.getData().trim().isEmpty()) {
            throw new IllegalArgumentException("Image data is required");
        }

        // Type-specific validation
        if ("base64".equals(imageSource.getType())) {
            validateBase64Image(imageSource.getData());
        } else if ("url".equals(imageSource.getType())) {
            validateImageUrl(imageSource.getData());
        }
    }

    /**
     * Validate base64-encoded image data.
     *
     * @param base64Data Base64 string to validate
     * @throws IllegalArgumentException if base64 data is invalid
     */
    private void validateBase64Image(String base64Data) {
        // Check if the string is valid base64
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(base64Data);

            // Check size limits
            if (decodedBytes.length > MAX_BASE64_SIZE_BYTES) {
                throw new IllegalArgumentException(
                    String.format(
                        "Image size exceeds maximum limit: %d bytes > %d bytes (5MB). " +
                        "Consider resizing the image or using a URL instead.",
                        decodedBytes.length,
                        MAX_BASE64_SIZE_BYTES
                    )
                );
            }
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("size exceeds")) {
                throw e;
            }
            throw new IllegalArgumentException(
                "Invalid base64 encoding in image data. Ensure the image is properly base64-encoded."
            );
        }
    }

    /**
     * Validate image URL.
     *
     * @param url URL string to validate
     * @throws IllegalArgumentException if URL is invalid
     */
    private void validateImageUrl(String url) {
        // Check that URL doesn't contain obvious invalid characters first
        if (url.contains(" ") || url.contains("\n") || url.contains("\r") || url.contains("\t")) {
            throw new IllegalArgumentException(
                "Image URL contains invalid whitespace characters"
            );
        }

        if (!URL_PATTERN.matcher(url).matches()) {
            throw new IllegalArgumentException(
                String.format(
                    "Invalid image URL format: %s. URL must be a valid HTTP/HTTPS URL.",
                    url
                )
            );
        }
    }
}
