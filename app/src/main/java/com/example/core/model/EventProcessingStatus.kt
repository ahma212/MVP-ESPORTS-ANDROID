package com.example.core.model

/**
 * Processing state of an esports detected event.
 * Events with confidence >= 50% are automatically processed.
 * Events with confidence < 50% are sent to Admin Review.
 */
enum class EventProcessingStatus {
    PENDING_ADMIN_REVIEW,
    AUTO_PROCESSED,
    ADMIN_CONFIRMED,
    ADMIN_REJECTED
}
