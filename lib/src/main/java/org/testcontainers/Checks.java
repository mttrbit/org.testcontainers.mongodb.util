package org.testcontainers;

final class Checks {
    private Checks() {
    }

    public static <T> T checkNotNull(T value, String name) {
        if (value == null) {
            throw new NullPointerException(name + " must not be null");
        }
        return value;
    }

    public static void checkArgument(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
