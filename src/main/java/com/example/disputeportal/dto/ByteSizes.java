package com.example.disputeportal.dto;

import java.util.Locale;

/** Formats byte counts for people ("340 KB", "1.2 MB"). */
final class ByteSizes {

    private static final long KB = 1024;
    private static final long MB = 1024 * 1024;

    private ByteSizes() {
    }

    static String format(long bytes) {
        if (bytes < KB) {
            return bytes + " B";
        }
        if (bytes < MB) {
            return ((bytes + KB / 2) / KB) + " KB";
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (double) MB);
    }
}
