package com.virjar.sekiro.utils;


import android.support.annotation.Nullable;

/**
 * This class provides default values for all Java types, as defined by the JLS.
 *
 * @author Ben Yu
 * @since 1.0
 */
public final class Defaults {
    private Defaults() {
    }

    private static final Double DOUBLE_DEFAULT = Double.valueOf(0d);
    private static final Float FLOAT_DEFAULT = Float.valueOf(0f);

    /**
     * Returns the default value of {@code type} as defined by JLS --- {@code 0} for numbers, {@code
     * false} for {@code boolean} and {@code '\0'} for {@code char}. For non-primitive types and
     * {@code void}, {@code null} is returned.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> T defaultValue(Class<T> type) {
        if (type == boolean.class) {
            return (T) Boolean.FALSE;
        } else if (type == char.class) {
            return (T) Character.valueOf('\0');
        } else if (type == byte.class) {
            return (T) Byte.valueOf((byte) 0);
        } else if (type == short.class) {
            return (T) Short.valueOf((short) 0);
        } else if (type == int.class) {
            return (T) Integer.valueOf(0);
        } else if (type == long.class) {
            return (T) Long.valueOf(0L);
        } else if (type == float.class) {
            return (T) FLOAT_DEFAULT;
        } else if (type == double.class) {
            return (T) DOUBLE_DEFAULT;
        } else {
            return null;
        }
    }
}
