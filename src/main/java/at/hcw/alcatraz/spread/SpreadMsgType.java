package at.hcw.alcatraz.spread;

public enum SpreadMsgType {
    UPDATE,
    RESET,
    START;

    public static SpreadMsgType from(String raw) {
        try {
            return valueOf(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
