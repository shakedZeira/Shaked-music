package com.example.spotifyreplica.domain.model

/** Lifecycle of a single download-queue entry. */
enum class DownloadStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    companion object {
        /**
         * Parses a persisted status string without throwing. Unknown or null values fall back to
         * [QUEUED] so a corrupted/legacy row can never crash a collector.
         */
        fun fromString(value: String?): DownloadStatus =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: QUEUED

        /** Alias kept for callers that think in terms of `valueOf`. */
        fun safeValueOf(value: String?): DownloadStatus = fromString(value)
    }
}
