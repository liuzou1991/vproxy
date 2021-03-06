package vswitch.packet;

import vproxy.util.ByteArray;
import vswitch.util.Consts;
import vswitch.util.SwitchUtils;

import java.util.Objects;

public class IcmpPacket extends AbstractPacket {
    private int type;
    private int code;
    private int checksum;
    private ByteArray other;

    private final boolean isIpv6;

    public IcmpPacket(boolean isIpv6) {
        this.isIpv6 = isIpv6;
    }

    @Override
    public String from(ByteArray bytes) {
        if (bytes.length() < 8) {
            return "input packet length too short for a icmp packet";
        }
        type = bytes.uint8(0);
        code = bytes.uint8(1);
        checksum = bytes.uint16(2);
        other = bytes.sub(4, bytes.length() - 4);

        raw = bytes;
        return null;
    }

    @Override
    protected ByteArray buildPacket() {
        if (isIpv6)
            throw new UnsupportedOperationException("this packet is ICMPv6");

        ByteArray ret = ByteArray.allocate(4).set(0, (byte) type).set(1, (byte) code)/*skip checksum here*/.concat(other);
        checksum = SwitchUtils.calculateChecksum(ret, ret.length());
        ret.int16(2, checksum);
        return ret;
    }

    public ByteArray getRawICMPv6Packet(Ipv6Packet ipv6) {
        if (!isIpv6)
            throw new UnsupportedOperationException("this packet is ICMP, not v6");
        if (raw == null) {
            raw = buildICMPv6Packet(ipv6);
        }
        return raw;
    }

    private ByteArray buildICMPv6Packet(Ipv6Packet ipv6) {
        if (!isIpv6)
            throw new UnsupportedOperationException("this packet is ICMP, not v6");

        ByteArray ret = ByteArray.allocate(4).set(0, (byte) type).set(1, (byte) code)/*skip checksum here*/.concat(other);

        ByteArray pseudoHeader = SwitchUtils.buildPseudoIPv6Header(ipv6, Consts.IP_PROTOCOL_ICMPv6, ret.length());

        ByteArray toCalculate = pseudoHeader.concat(ret);
        checksum = SwitchUtils.calculateChecksum(toCalculate, toCalculate.length());
        ret.int16(2, checksum);
        return ret;
    }

    @Override
    public String toString() {
        return "IcmpPacket{" +
            "type=" + type +
            ", code=" + code +
            ", checksum=" + checksum +
            ", other=" + other +
            '}';
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        clearRawPacket();
        this.type = type;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        clearRawPacket();
        this.code = code;
    }

    public int getChecksum() {
        return checksum;
    }

    public void setChecksum(int checksum) {
        clearRawPacket();
        this.checksum = checksum;
    }

    public ByteArray getOther() {
        return other;
    }

    public void setOther(ByteArray other) {
        clearRawPacket();
        this.other = other;
    }

    public boolean isIpv6() {
        return isIpv6;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IcmpPacket that = (IcmpPacket) o;
        return type == that.type &&
            code == that.code &&
            checksum == that.checksum &&
            Objects.equals(other, that.other) &&
            isIpv6 == that.isIpv6;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, code, checksum, other, isIpv6);
    }
}
