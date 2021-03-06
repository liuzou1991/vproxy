package vproxy.socks;

import vfd.IP;
import vproxy.component.proxy.ConnectorProvider;
import vproxy.connection.Connection;
import vproxy.connection.Connector;

import java.util.function.Consumer;

public interface Socks5ConnectorProvider extends ConnectorProvider {
    void provide(Connection accepted, AddressType type, String address, int port, Consumer<Connector> providedCallback);

    @Override
    default void provide(Connection accepted, String address, int port, Consumer<Connector> providedCallback) {
        if (IP.isIpv4(address)) {
            provide(accepted, AddressType.ipv4, address, port, providedCallback);
        } else if (IP.isIpv6(address)) {
            provide(accepted, AddressType.ipv6, address, port, providedCallback);
        } else {
            provide(accepted, AddressType.domain, address, port, providedCallback);
        }
    }
}
