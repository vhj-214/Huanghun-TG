package org.telegram.messenger;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Collects file URIs from Android share intents without assuming that every
 * provider uses the same EXTRA_STREAM representation.
 */
public final class SharedFileUriHelper {
    private SharedFileUriHelper() {
    }

    public static boolean isPhotoOrVideoMimeType(String mimeType) {
        return mimeType != null && (mimeType.startsWith("image/") || mimeType.startsWith("video/"));
    }

    public static ArrayList<Uri> collect(Intent intent) {
        if (intent == null) {
            return null;
        }
        ArrayList<Uri> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        try {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                appendValue(extras.get(Intent.EXTRA_STREAM), result, seen);
            }
        } catch (Throwable ignored) {
            // ClipData and Intent.data may still contain a usable grant.
        }

        try {
            ClipData clipData = intent.getClipData();
            if (clipData != null) {
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    try {
                        ClipData.Item item = clipData.getItemAt(i);
                        if (item != null) {
                            appendUri(item.getUri(), result, seen);
                        }
                    } catch (Throwable ignored) {
                        // A malformed item must not discard other shared files.
                    }
                }
            }
        } catch (Throwable ignored) {
            // Continue with the data URI fallback.
        }

        try {
            appendUri(intent.getData(), result, seen);
        } catch (Throwable ignored) {
            // No usable data URI.
        }
        return result.isEmpty() ? null : result;
    }

    private static void appendValue(Object value, ArrayList<Uri> result, Set<String> seen) {
        if (value == null) {
            return;
        }
        if (value instanceof Uri) {
            appendUri((Uri) value, result, seen);
        } else if (value instanceof String) {
            try {
                appendUri(Uri.parse((String) value), result, seen);
            } catch (Throwable ignored) {
                // Ignore malformed provider values.
            }
        } else if (value instanceof ArrayList<?>) {
            for (Object item : (ArrayList<?>) value) {
                try {
                    appendValue(item, result, seen);
                } catch (Throwable ignored) {
                    // Keep collecting the remaining entries.
                }
            }
        } else if (value instanceof Object[]) {
            for (Object item : (Object[]) value) {
                try {
                    appendValue(item, result, seen);
                } catch (Throwable ignored) {
                    // Keep collecting the remaining entries.
                }
            }
        }
    }

    private static void appendUri(Uri uri, ArrayList<Uri> result, Set<String> seen) {
        if (uri == null) {
            return;
        }
        String scheme;
        String key;
        try {
            scheme = uri.getScheme();
            key = uri.toString();
        } catch (Throwable ignored) {
            return;
        }
        if (key == null || key.trim().isEmpty() || scheme == null ||
                !("content".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme))) {
            return;
        }
        if (seen.add(key)) {
            result.add(uri);
        }
    }
}
