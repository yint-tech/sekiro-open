package com.virjar.sekiro.api;

import android.text.TextUtils;

public class NameValuePair {
    private final String name;
    private final String value;

    /**
     * Default Constructor taking a name and a value. The value may be null.
     *
     * @param name  The name.
     * @param value The value.
     */
    public NameValuePair(final String name, final String value) {
        super();
        if (name == null) {
            throw new IllegalArgumentException("Name may not be null");
        }
        this.name = name;
        this.value = value;
    }

    /**
     * Returns the name.
     *
     * @return String name The name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Returns the value.
     *
     * @return String value The current value.
     */
    public String getValue() {
        return this.value;
    }


    /**
     * Get a string representation of this pair.
     *
     * @return A string representation.
     */
    public String toString() {
        return name + "=" + value;
    }

    public boolean equals(final Object object) {
        if (object == null) return false;
        if (this == object) return true;
        if (object instanceof NameValuePair) {
            NameValuePair that = (NameValuePair) object;
            return this.name.equals(that.name)
                    && TextUtils.equals(this.value, that.value);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return name.hashCode() ^ value.hashCode();
    }

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

}
