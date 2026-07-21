package io.github.duckasteroid.cthugha.remote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;

public class NetworkUtils {

    private static final Logger LOG = LoggerFactory.getLogger(NetworkUtils.class);

    private NetworkUtils() {}

    /**
     * Returns the best-effort base URL for the remote server.
     *
     * <p>Prefers the first IPv4 address on a non-loopback, non-virtual, up interface.
     * If {@code preferredInterface} is non-null it is matched by name and trusted even
     * if the OS marks it virtual (useful for USB-tethered phones).</p>
     */
    public static String detectBaseUrl(RemoteConfig config) {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                boolean named = config.networkInterface != null
                        && config.networkInterface.equalsIgnoreCase(ni.getName());
                LOG.debug("Network interface: {} loopback={} up={} virtual={} named={}",
                        ni.getName(), ni.isLoopback(), ni.isUp(), ni.isVirtual(), named);
                if (ni.isLoopback() || !ni.isUp() || ni.isVirtual()) continue;
                if (config.networkInterface != null && !named) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    LOG.debug("  address: {} ({})", addr.getHostAddress(), addr.getClass().getSimpleName());
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return "http://" + addr.getHostAddress() + ":" + config.port + "/";
                    }
                }
            }
        } catch (SocketException e) {
            LOG.warn("Could not detect local IP", e);
        }
        LOG.warn("No suitable network interface found (configured: {}), falling back to localhost",
                config.networkInterface);
        return "http://localhost:" + config.port + "/";
    }
}
