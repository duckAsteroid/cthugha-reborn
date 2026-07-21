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
    /** Jetty thread pool minimum. 2 is enough to keep an idle acceptor + selector alive. */
    public int minJettyThreads = 2;
    /** Jetty thread pool maximum. 8 comfortably handles 2 SSE clients plus burst REST calls. */
    public int maxJettyThreads = 8;
    /** Minimum milliseconds between SSE broadcasts for animation-controlled parameters (default 100 = 10 fps). */
    public long animationBroadcastIntervalMs = 100;
    /** Fixed bearer token, e.g. for local dev so the same URL survives app restarts. Null = random per-rotation (default). */
    public String fixedToken = null;
    /** Whether the Screenshot/Record/Stop Recording buttons appear in the remote UI header. */
    public boolean screenCaptureToolbar = true;

    public static RemoteConfig parse(String[] args) {
        RemoteConfig config = new RemoteConfig();

        String iniEnabled   = Config.singleton().getConfig("remote", "enabled", "");
        String iniPort      = Config.singleton().getConfig("remote", "port", "8080");
        String iniQr        = Config.singleton().getConfig("remote", "qr_display_seconds", "30");
        String iniQrLogo    = Config.singleton().getConfig("remote", "qr_logo_size", "0");
        String iniIface     = Config.singleton().getConfig("remote", "network_interface", "");
        String iniMinThreads = Config.singleton().getConfig("remote", "min_threads", "");
        String iniMaxThreads = Config.singleton().getConfig("remote", "max_threads", "");
        String iniScreenCaptureToolbar = Config.singleton().getConfig("remote", "screen_capture_toolbar", "true");
        if (!iniEnabled.isBlank()) config.enabled = Boolean.parseBoolean(iniEnabled.trim());
        if (!iniScreenCaptureToolbar.isBlank()) config.screenCaptureToolbar = Boolean.parseBoolean(iniScreenCaptureToolbar.trim());
        try { config.port = Integer.parseInt(iniPort.trim()); } catch (NumberFormatException ignored) {}
        try { config.qrTimeoutSeconds = Integer.parseInt(iniQr.trim()); } catch (NumberFormatException ignored) {}
        try { config.qrLogoPercent = Math.min(30, Math.max(0, Integer.parseInt(iniQrLogo.trim()))); } catch (NumberFormatException ignored) {}
        if (!iniIface.isBlank()) config.networkInterface = iniIface.trim();
        try { config.minJettyThreads = Integer.parseInt(iniMinThreads.trim()); } catch (NumberFormatException ignored) {}
        try { config.maxJettyThreads = Integer.parseInt(iniMaxThreads.trim()); } catch (NumberFormatException ignored) {}
        String iniAnimInterval = Config.singleton().getConfig("remote", "animation_broadcast_interval_ms", "");
        try { config.animationBroadcastIntervalMs = Long.parseLong(iniAnimInterval.trim()); } catch (NumberFormatException ignored) {}
        String iniToken = Config.singleton().getConfig("remote", "token", "");
        if (!iniToken.isBlank()) config.fixedToken = iniToken.trim();

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
            } else if (arg.startsWith("--remote-token=")) {
                config.fixedToken = arg.substring("--remote-token=".length());
            }
        }

        return config;
    }
}
