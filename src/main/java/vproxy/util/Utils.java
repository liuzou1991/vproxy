package vproxy.util;

import sun.misc.Unsafe;
import vfd.FDProvider;
import vfd.IP;
import vfd.IPPort;
import vproxy.connection.Connector;
import vproxy.dns.Resolver;
import vproxy.socks.AddressType;

import java.io.*;
import java.lang.reflect.Field;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Utils {
    public static final String RESET_MSG = "Connection reset by peer";
    public static final String BROKEN_PIPE_MSG = "Broken pipe";
    public static final String SSL_ENGINE_CLOSED_MSG = "SSLEngine closed";
    public static final String HOST_IS_DOWN_MSG = "Host is down";
    public static final String NO_ROUTE_TO_HOST_MSG = "No route to host";
    @SuppressWarnings("unused")
    private static volatile int sync = 0; // this filed is used to sync cpu cache into memory

    private Utils() {
    }

    public static void syncCpuCacheAndMemory() {
        //noinspection NonAtomicOperationOnVolatileField
        ++sync;
    }

    public static int positive(byte b) {
        if (b < 0) return 256 + b;
        return b;
    }

    public static int positive(short s) {
        if (s < 0) return 32768 + s;
        return s;
    }

    public static String homedir() {
        return System.getProperty("user.home");
    }

    public static String filename(String s) {
        if (s.startsWith("~")) {
            s = homedir() + s.substring(1);
        }
        return s;
    }

    public static String homefile(String s) {
        return homedir() + File.separator + s;
    }

    private static String addTo(@SuppressWarnings("SameParameterValue") int len, String s) {
        if (s.length() >= len)
            return s;
        StringBuilder sb = new StringBuilder();
        //noinspection StringRepeatCanBeUsed
        for (int i = s.length(); i < len; ++i) {
            sb.append("0");
        }
        sb.append(s);
        return sb.toString();
    }

    private static String formatErrBase(Throwable err) {
        if (err.getMessage() != null && !err.getMessage().isBlank()) {
            return err.getMessage().trim();
        } else {
            return err.toString();
        }
    }

    public static String formatErr(Throwable err) {
        String base = formatErrBase(err);
        if (err instanceof RuntimeException) {
            return base + Arrays.asList(err.getStackTrace()).toString();
        } else {
            return base;
        }
    }

    public static int zeros(byte b) {
        if ((b & /*-------*/0b1) == /*-------*/0b1) return 0;
        if ((b & /*------*/0b10) == /*------*/0b10) return 1;
        if ((b & /*-----*/0b100) == /*-----*/0b100) return 2;
        if ((b & /*----*/0b1000) == /*----*/0b1000) return 3;
        if ((b & /*---*/0b10000) == /*---*/0b10000) return 4;
        if ((b & /*--*/0b100000) == /*--*/0b100000) return 5;
        if ((b & /*-*/0b1000000) == /*-*/0b1000000) return 6;
        if ((b & /**/0b10000000) == /**/0b10000000) return 7;
        return 8;
    }

    public static byte[] long2bytes(long v) {
        LinkedList<Byte> bytes = new LinkedList<>();
        while (v != 0) {
            byte b = (byte) (v & 0xff);
            bytes.addFirst(b);
            v = v >> 8;
        }
        byte[] ret = new byte[bytes.size()];
        int idx = 0;
        for (byte b : bytes) {
            ret[idx] = b;
            ++idx;
        }
        return ret;
    }

    public static boolean lowBitsV6V4(byte[] ip, int lastLowIdx, int secondLastLowIdx) {
        for (int i = 0; i < secondLastLowIdx; ++i) {
            if (ip[i] != 0)
                return false;
        }
        if (ip[lastLowIdx] == 0) {
            return ip[secondLastLowIdx] == 0;
        } else if (ip[lastLowIdx] == ((byte) 0b11111111)) {
            return ip[secondLastLowIdx] == ((byte) 0b11111111);
        } else
            return false;
    }

    // specify the number of 1 in the head of bit sequence
    // and return a byte
    public static byte getByte(int ones) {
        switch (ones) {
            case 8:
                return (byte) 0b11111111;
            case 7:
                return (byte) 0b11111110;
            case 6:
                return (byte) 0b11111100;
            case 5:
                return (byte) 0b11111000;
            case 4:
                return (byte) 0b11110000;
            case 3:
                return (byte) 0b11100000;
            case 2:
                return (byte) 0b11000000;
            case 1:
                return (byte) 0b10000000;
            default:
                // if <= 0, return 0
                // the `getMask()` method can be more simple
                return 0;
        }
    }

    public static String[] split(String str, String e) {
        List<String> ls = new LinkedList<>();
        int idx = -e.length();
        int lastIdx = 0;
        while (true) {
            idx = str.indexOf(e, idx + e.length());
            if (idx == -1) {
                ls.add(str.substring(lastIdx));
                break;
            }
            ls.add(str.substring(lastIdx, idx));
            lastIdx = idx + e.length();
        }
        //noinspection ToArrayCallWithZeroLengthArrayArgument
        return ls.toArray(new String[ls.size()]);
    }

    private static Unsafe U;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            U = (Unsafe) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Logger.shouldNotHappen("Reflection failure: get unsafe failed " + e);
            throw new RuntimeException(e);
        }
    }

    public static void clean(ByteBuffer buffer) {
        assert Logger.lowLevelDebug("run Utils.clean");
        if (!buffer.getClass().getName().equals("java.nio.DirectByteBuffer")) {
            assert Logger.lowLevelDebug("not direct buffer");
            return;
        }
        assert Logger.lowLevelDebug("is direct buffer, do clean");
        U.invokeCleaner(buffer);
    }

    public static void directConnect(AddressType type, String address, int port, Consumer<Connector> providedCallback) {
        if (type == AddressType.domain) { // resolve if it's domain
            Resolver.getDefault().resolve(address, new Callback<>() {
                @Override
                protected void onSucceeded(IP value) {
                    providedCallback.accept(new Connector(new IPPort(value, port)));
                }

                @Override
                protected void onFailed(UnknownHostException err) {
                    // resolve failed
                    assert Logger.lowLevelDebug("resolve for " + address + " failed in socks5 server" + err);
                    providedCallback.accept(null);
                }
            });
        } else {
            if (!IP.isIpLiteral(address)) {
                assert Logger.lowLevelDebug("client request with an invalid ip " + address);
                providedCallback.accept(null);
                return;
            }
            IP remote = IP.from(address);
            providedCallback.accept(new Connector(new IPPort(remote, port)));
        }
    }

    public static long currentMinute() {
        return
            (FDProvider.get().currentTimeMillis() / 60_000 // remove millis and seconds
            ) * 60_000 // get minutes
            ;
    }

    public static void shiftLeft(byte[] arr, int l) {
        for (int i = 0; i < arr.length; ++i) {
            int e = i + l;
            byte b = e >= arr.length ? 0 : arr[e];
            arr[i] = b;
        }
    }

    public static boolean isReset(IOException t) {
        return RESET_MSG.equals(t.getMessage());
    }

    public static boolean isBrokenPipe(IOException t) {
        return BROKEN_PIPE_MSG.equals(t.getMessage());
    }

    public static boolean isSSLEngineClosed(IOException t) {
        return SSL_ENGINE_CLOSED_MSG.equals(t.getMessage());
    }

    public static boolean isTerminatedIOException(IOException t) {
        return isReset(t) || isBrokenPipe(t) || isSSLEngineClosed(t);
    }

    public static boolean isHostIsDown(IOException t) {
        return HOST_IS_DOWN_MSG.equals(t.getMessage());
    }

    public static boolean isNoRouteToHost(IOException t) {
        return NO_ROUTE_TO_HOST_MSG.equals(t.getMessage());
    }

    public static String stackTrace() {
        StringWriter s = new StringWriter();
        new Throwable().printStackTrace(new PrintWriter(s));
        return s.toString();
    }

    public static int writeFromFIFOQueueToBufferPacketBound(Deque<ByteBuffer> bufs, ByteBuffer dst) {
        int ret = 0;
        while (true) {
            if (bufs.isEmpty()) {
                // src is empty
                break;
            }
            ByteBuffer b = bufs.peek();
            int bufLim = b.limit();
            int bufPos = b.position();
            if (bufLim - bufPos == 0) {
                bufs.poll();
                continue;
            }
            int dstLim = dst.limit();
            int dstPos = dst.position();

            if (dstLim - dstPos == 0) {
                // dst is full
                break;
            }

            if (dstLim - dstPos < bufLim - bufPos) {
                // we consider packet bound
                // so should not write partial data into the dst
                break;
            }

            ret += (b.limit() - b.position());
            dst.put(b);
        }
        return ret;
    }

    public static int writeFromFIFOQueueToBuffer(Deque<ByteBuffer> bufs, ByteBuffer dst) {
        int ret = 0;
        while (true) {
            if (bufs.isEmpty()) {
                // src is empty
                break;
            }
            ByteBuffer b = bufs.peek();
            int oldLim = b.limit();
            int oldPos = b.position();
            if (oldLim - oldPos == 0) {
                bufs.poll();
                continue;
            }
            int dstLim = dst.limit();
            int dstPos = dst.position();

            if (dstLim - dstPos == 0) {
                // dst is full
                break;
            }

            if (dstLim - dstPos < oldLim - oldPos) {
                b.limit(oldPos + (dstLim - dstPos));
            }
            ret += (b.limit() - b.position());
            dst.put(b);
            b.limit(oldLim);
        }
        return ret;
    }

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static boolean debug(Runnable r) {
        //noinspection ConstantConditions,TrivialFunctionalExpressionUsage
        assert ((BooleanSupplier) () -> {
            r.run();
            return true;
        }).getAsBoolean();
        return true;
    }

    public static byte[] gzipCompress(ByteArrayOutputStream baos, byte[] plain) {
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos) {
            {
                this.def.setLevel(Deflater.BEST_COMPRESSION);
            }
        }) {
            gzip.write(plain);
        } catch (IOException e) {
            Logger.shouldNotHappen("running gzip compression failed", e);
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    public static byte[] gzipDecompress(ByteArrayOutputStream baos, byte[] compressed) {
        ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
        try (GZIPInputStream gzip = new GZIPInputStream(bais)) {
            byte[] buf = new byte[1024];
            int n;
            while ((n = gzip.read(buf, 0, buf.length)) >= 0) {
                baos.write(buf, 0, n);
            }
        } catch (IOException e) {
            Logger.shouldNotHappen("running gzip decompression failed", e);
            return null;
        }
        return baos.toByteArray();
    }

    public static boolean assertOn() {
        try {
            assert false;
            return false;
        } catch (AssertionError ignore) {
            return true;
        }
    }

    public interface UtilSupplier<T> {
        T get() throws Exception;
    }

    public static <T> T runBlockWithTimeout(int millis, UtilSupplier<T> f) throws Exception {
        BlockCallback<T, Exception> cb = new BlockCallback<>();
        new Thread(() -> {
            T t;
            try {
                t = f.get();
            } catch (Exception e) {
                if (!cb.isCalled()) {
                    cb.onFailed(e);
                }
                return;
            }
            if (!cb.isCalled()) {
                cb.onSucceeded(t);
            }
        }).start();
        new Thread(() -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ignore) {
            }
            if (!cb.isCalled()) {
                cb.onFailed(new TimeoutException("operation time out"));
            }
        }).start();
        return cb.block();
    }

    public static <T> T runAvoidNull(Supplier<T> f, T dft) {
        try {
            return f.get();
        } catch (NullPointerException e) {
            return dft;
        }
    }

    public static String toHexString(int x) {
        return "0x" + Integer.toHexString(x);
    }

    public static String toHexStringWithPadding(int x, int bits) {
        assert bits % 8 == 0;
        int len = bits / 4;
        String s = Integer.toHexString(x);
        if (s.length() < len) {
            s = "0".repeat(len - s.length()) + s;
        }
        return "0x" + s;
    }

    public static String toBinaryString(int x) {
        return "0b" + Integer.toBinaryString(x);
    }

    public static boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    public static void pipeOutputOfSubProcess(Process p) {
        var stdout = p.getInputStream();
        var stderr = p.getErrorStream();
        new Thread(() -> {
            var br = new BufferedReader(new InputStreamReader(stdout));
            String x;
            try {
                while ((x = br.readLine()) != null) {
                    System.out.println(x);
                }
            } catch (Throwable ignore) {
            }
            try {
                stdout.close();
            } catch (Throwable ignore) {
            }
        }).start();
        new Thread(() -> {
            var br = new BufferedReader(new InputStreamReader(stderr));
            String x;
            try {
                while ((x = br.readLine()) != null) {
                    System.out.println(x);
                }
            } catch (Throwable ignore) {
            }
            try {
                stderr.close();
            } catch (Throwable ignore) {
            }
        }).start();
    }

    // the returned array would be without getStackTrace() and this method
    public static StackTraceElement[] stackTraceStartingFromThisMethodInclusive() {
        final String meth = "stackTraceStartingFromThisMethodInclusive";
        StackTraceElement[] arr = Thread.currentThread().getStackTrace();
        int i = 0;
        for (StackTraceElement elem : arr) {
            i += 1;
            if (elem.getMethodName().equals(meth)) {
                break;
            }
        }
        StackTraceElement[] ret = new StackTraceElement[arr.length - i];
        System.arraycopy(arr, i, ret, 0, ret.length);
        return ret;
    }

    public static void exit(int code) {
        System.exit(code);
    }
}
