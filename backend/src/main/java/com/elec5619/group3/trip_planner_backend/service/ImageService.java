package com.elec5619.group3.trip_planner_backend.service;

import com.elec5619.group3.trip_planner_backend.entity.StoredImage;
import com.elec5619.group3.trip_planner_backend.exception.ResourceNotFoundException;
import com.elec5619.group3.trip_planner_backend.repository.StoredImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Stores and serves uploaded images (trip covers; reusable for profile photos).
 */
@Service
public class ImageService {

    private final StoredImageRepository imageRepository;

    public ImageService(StoredImageRepository imageRepository) {
        this.imageRepository = imageRepository;
    }

    /**
     * Validate and save an uploaded image. Only JPEG, PNG and WebP are
     * accepted, detected from the file's bytes rather than its claimed type
     * (so e.g. an SVG or HTML file can't be served back as an "image").
     */
    @Transactional
    public StoredImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please choose an image to upload");
        }
        if (file.getSize() > StoredImage.MAX_BYTES) {
            throw new IllegalArgumentException("Image must be 5 MB or smaller");
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded image");
        }

        String contentType = detectContentType(data);
        if (contentType == null) {
            throw new IllegalArgumentException("Image must be a JPEG, PNG or WebP file");
        }

        StoredImage image = new StoredImage();
        image.setImageId(UUID.randomUUID().toString());
        image.setContentType(contentType);
        image.setData(data);
        return imageRepository.save(image);
    }

    @Transactional(readOnly = true)
    public StoredImage get(String imageId) {
        return imageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));
    }

    /** Delete an image if it exists; null is ignored. */
    @Transactional
    public void delete(String imageId) {
        if (imageId != null && imageRepository.existsById(imageId)) {
            imageRepository.deleteById(imageId);
        }
    }

    private static String detectContentType(byte[] data) {
        if (startsWith(data, 0, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(data, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (startsWith(data, 0, 'R', 'I', 'F', 'F') && startsWith(data, 8, 'W', 'E', 'B', 'P')) {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] data, int offset, int... signature) {
        if (data.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
