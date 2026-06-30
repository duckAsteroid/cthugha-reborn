package io.github.duckasteroid.cthugha.remote;

import io.github.duckasteroid.cthugha.config.Config;

public class RemoteConfig {

    public boolean enabled = false;
    public int port = 8080;
    public int qrTimeoutSeconds = 30;
    /** Logo area as a percentage (0 = no logo, 30 = 30% of QR area). Max 30 (HIGH ECC limit). */
    public int qrLogoPercent = 0;
    /** Network interface name to advertise in the QR URL (e.g. "wlan0", "eth0"). Null = auto-detect. */
    public String networkInterface = null;

    public static RemoteConfig parse(String[] args) {
        RemoteConfig config = new RemoteConfig();

        String iniEnabled = Config.singleton().getConfig("remote", "enabled", "");
        String iniPort = Config.singleton().getConfig("remote", "port", "8080");
        String iniQr = Config.singleton().getConfig("remote", "qr_display_seconds", "30");
        String iniQrLogo = Config.singleton().getConfig("remote", "qr_logo_size", "0");
        String iniIface = Config.singleton().getConfig("remote", "network_interface", "");
        if (!iniEnabled.isBlank()) config.enabled = Boolean.parseBoolean(iniEnabled.trim());
        try { config.port = Integer.parseInt(iniPort.trim()); } catch (NumberFormatException ignored) {}
        try { config.qrTimeoutSeconds = Integer.parseInt(iniQr.trim()); } catch (NumberFormatException ignored) {}
        try { config.qrLogoPercent = Math.min(30, Math.max(0, Integer.parseInt(iniQrLogo.trim()))); } catch (NumberFormatException ignored) {}
        if (!iniIface.isBlank()) config.networkInterface = iniIface.trim();

        for (String arg : args) {
            if (arg.equals("--no-remote")) {
                config.enabled = false;
                break;
            } else if (arg.startsWith("--remote-port=")) {
                config.port = Integer.parseInt(arg.substring("--remote-port=".length()));
            } else if (arg.startsWith("--remote-qr-timeout=")) {
                config.qrTimeoutSeconds = Integer.parseInt(arg.substring("--remote-qr-timeout=".length()));
            } else if (arg.startsWith("--remote-interface=")) {
                config.networkInterface = arg.substring("--remote-interface=".length());
            }
        }

        return config;
    }
}
