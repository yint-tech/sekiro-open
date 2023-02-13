package cn.iinti.sekiro3.open.framework.trace;


import lombok.Getter;
import lombok.Setter;

public abstract class Recorder {
    @Getter
    private final String sessionId;

    @Getter
    @Setter
    private String subTitle;

    public Recorder(String sessionId) {
        this.sessionId = sessionId;
    }

    public void recordEvent(String message) {
        recordEvent(() -> message, null);
    }

    public void recordEvent(String message, Throwable throwable) {
        recordEvent(() -> message, throwable);
    }

    public void recordEvent(MessageGetter messageGetter) {
        recordEvent(messageGetter, null);
    }

    public abstract void recordEvent(MessageGetter messageGetter, Throwable throwable);

    public boolean enable() {
        return false;
    }

    public interface MessageGetter {
        String getMessage();
    }

}
