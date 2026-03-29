package com.blackaby.Backend.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;

abstract class KeyedPropertiesStore {

    private final String entryPrefix;
    private final String keySuffix;
    private final String configuredPathProperty;
    private final Path defaultPath;
    private final String storeComment;
    private final Properties properties = new Properties();
    private boolean loaded;

    protected KeyedPropertiesStore(String entryPrefix, String keySuffix, String configuredPathProperty,
                                   Path defaultPath, String storeComment) {
        this.entryPrefix = entryPrefix;
        this.keySuffix = keySuffix;
        this.configuredPathProperty = configuredPathProperty;
        this.defaultPath = defaultPath;
        this.storeComment = storeComment;
    }

    protected final void EnsureLoaded() {
        if (loaded) {
            return;
        }

        properties.clear();
        Path path = StorePath();
        if (Files.exists(path)) {
            try (InputStream inputStream = Files.newInputStream(path)) {
                properties.load(inputStream);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
        loaded = true;
    }

    protected final void Persist() {
        Path path = StorePath();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream outputStream = Files.newOutputStream(path)) {
                properties.store(outputStream, storeComment);
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    protected final Entry Entry(String key) {
        return new Entry(key);
    }

    protected final String RawProperty(String key) {
        return properties.getProperty(key);
    }

    protected final void SetRawProperty(String key, String value) {
        properties.setProperty(key, NullToEmpty(value));
    }

    protected final List<String> StoredKeys() {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String propertyName : properties.stringPropertyNames()) {
            if (!propertyName.startsWith(entryPrefix) || !propertyName.endsWith(keySuffix)) {
                continue;
            }

            String key = propertyName.substring(entryPrefix.length(), propertyName.length() - keySuffix.length());
            if (!key.isBlank()) {
                keys.add(key);
            }
        }
        return List.copyOf(keys);
    }

    protected final void ResetForTests() {
        properties.clear();
        loaded = false;
    }

    protected static int ParseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    protected static long ParseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    protected static String NullToEmpty(String value) {
        return value == null ? "" : value;
    }

    protected static String Hash(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(value);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is unavailable.", exception);
        }
    }

    protected static String Hash(String value) {
        return Hash(value.getBytes(StandardCharsets.UTF_8));
    }

    private Path StorePath() {
        String configuredPath = System.getProperty(configuredPathProperty);
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath);
        }
        return defaultPath;
    }

    protected final class Entry {
        private final String key;
        private final String prefix;

        private Entry(String key) {
            this.key = key;
            this.prefix = entryPrefix + key;
        }

        protected String Key() {
            return key;
        }

        protected String Prefix() {
            return prefix;
        }

        protected boolean Has(String suffix) {
            return properties.getProperty(prefix + suffix) != null;
        }

        protected String Get(String suffix) {
            return properties.getProperty(prefix + suffix, "");
        }

        protected void Set(String suffix, String value) {
            properties.setProperty(prefix + suffix, NullToEmpty(value));
        }

        protected void Set(String suffix, boolean value) {
            properties.setProperty(prefix + suffix, String.valueOf(value));
        }

        protected void Set(String suffix, int value) {
            properties.setProperty(prefix + suffix, String.valueOf(value));
        }

        protected void Set(String suffix, long value) {
            properties.setProperty(prefix + suffix, String.valueOf(value));
        }

        protected int GetInt(String suffix, int fallback) {
            return ParseInt(Get(suffix), fallback);
        }

        protected long GetLong(String suffix, long fallback) {
            return ParseLong(Get(suffix), fallback);
        }

        protected boolean GetBoolean(String suffix, boolean fallback) {
            return Boolean.parseBoolean(properties.getProperty(prefix + suffix, String.valueOf(fallback)));
        }

        protected void WriteIndexedList(String itemPrefix, String countSuffix, List<String> values) {
            int previousCount = GetInt(countSuffix, 0);
            for (int index = 0; index < previousCount; index++) {
                properties.remove(prefix + itemPrefix + index);
            }

            List<String> safeValues = values == null ? List.of() : values;
            Set(countSuffix, safeValues.size());
            for (int index = 0; index < safeValues.size(); index++) {
                Set(itemPrefix + index, safeValues.get(index));
            }
        }

        protected List<String> ReadIndexedList(String itemPrefix, String countSuffix) {
            int count = GetInt(countSuffix, 0);
            List<String> values = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                values.add(Get(itemPrefix + index));
            }
            return List.copyOf(values);
        }

        protected void RemoveAll() {
            List<String> propertyNames = new ArrayList<>(properties.stringPropertyNames());
            for (String propertyName : propertyNames) {
                if (propertyName.startsWith(prefix)) {
                    properties.remove(propertyName);
                }
            }
        }
    }
}
