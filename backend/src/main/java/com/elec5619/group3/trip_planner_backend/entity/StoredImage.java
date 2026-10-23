package com.elec5619.group3.trip_planner_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * An uploaded image (e.g. a trip cover photo), stored in the database so it
 * is available to everyone using the shared DB rather than one machine's disk.
 *
 * The id is a random UUID and doubles as the public URL key; a new id is
 * issued on every upload, so URLs can be cached forever.
 */
@Entity
@Table(name = "stored_images")
@Getter
@Setter
@NoArgsConstructor
public class StoredImage {

    public static final int MAX_BYTES = 5 * 1024 * 1024;

    @Id
    @Column(name = "image_id", length = 36)
    private String imageId;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(nullable = false, length = MAX_BYTES)
    private byte[] data;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
