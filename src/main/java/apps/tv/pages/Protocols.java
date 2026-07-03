package apps.tv.pages;

/**
 * VPN protocols available in the Android TV protocol grid (connect_mode).
 * The {@code label} matches the on-screen text.
 */
public enum Protocols {
    Auto("Auto"),
    IKEv2("IKEv2"),
    OpenVPN("OpenVPN");

    public final String label;

    Protocols(String label) {
        this.label = label;
    }
}
