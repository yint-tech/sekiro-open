package cn.iinti.sekiro3.open.framework.safethread;


public interface ValueCallback<T> {
    void onReceiveValue(Value<T> value);


    static <T> void success(ValueCallback<T> callback, T t) {
        callback.onReceiveValue(Value.success(t));
    }

    static <T> void failed(ValueCallback<T> callback, Throwable e) {
        callback.onReceiveValue(Value.failed(e));
    }

    static <T> void failed(ValueCallback<T> callback, String message) {
        callback.onReceiveValue(Value.failed(message));
    }

    class Value<T> {

        public T v;
        public Throwable e;

        public boolean isSuccess() {
            return e == null;
        }

        public static <T> Value<T> failed(Throwable e) {
            Value<T> value = new Value<>();
            value.e = e;
            return value;
        }

        public static <T> Value<T> failed(String message) {
            return failed(new RuntimeException(message));
        }

        public static <T> Value<T> success(T t) {
            Value<T> value = new Value<>();
            value.v = t;
            return value;
        }

        public <N> Value<N> errorTransfer() {
            Value<N> value = new Value<>();
            value.e = e;
            return value;
        }
    }
}
