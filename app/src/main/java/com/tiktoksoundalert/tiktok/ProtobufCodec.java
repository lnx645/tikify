package com.tiktoksoundalert.tiktok;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * Minimal protobuf wire-format codec used by the TikTok WebCast protocol.
 * Splits a message into (fieldNumber -> list of raw values) and encodes
 * small control frames. Kept intentionally small and dependency-free.
 */
public final class ProtobufCodec {

    private ProtobufCodec() {
    }

    /** Parse a message into fieldNumber -> list of raw value bytes. Never null. */
    public static Map<Long, List<byte[]>> parse(byte[] b) {
        Map<Long, List<byte[]>> m = new TreeMap<>();
        if (b == null || b.length == 0) return m;
        PB pb = new PB(b);
        while (pb.more()) {
            long k = pb.varint();
            if (k < 0) break;
            long no = k >>> 3, wt = k & 7;
            byte[] v;
            if (wt == 0) {
                int s = pb.p;
                long z = pb.varint();
                if (z < 0) break;
                v = Arrays.copyOfRange(b, s, pb.p);
            } else if (wt == 2) {
                long l = pb.varint();
                if (l < 0 || l > b.length - pb.p) break;
                int s = pb.p;
                pb.p += (int) l;
                v = Arrays.copyOfRange(b, s, pb.p);
            } else if (wt == 1) {
                if (pb.p + 8 > b.length) break;
                pb.p += 8;
                v = new byte[0];
            } else if (wt == 5) {
                if (pb.p + 4 > b.length) break;
                pb.p += 4;
                v = new byte[0];
            } else {
                break;
            }
            List<byte[]> list = m.get(no);
            if (list == null) {
                list = new ArrayList<>();
                m.put(no, list);
            }
            list.add(v);
        }
        return m;
    }

    public static long varint(byte[] v) {
        if (v == null || v.length == 0) return 0;
        return new PB(v).varint();
    }

    public static String str(byte[] v) {
        if (v == null || v.length == 0) return "";
        return new String(v, StandardCharsets.UTF_8);
    }

    public static byte[] first(Map<Long, List<byte[]>> f, long n) {
        List<byte[]> l = f.get(n);
        return (l == null || l.isEmpty()) ? null : l.get(0);
    }

    public static String hexPrefix(byte[] b) {
        if (b == null || b.length == 0) return "";
        StringBuilder h = new StringBuilder();
        int n = Math.min(b.length, 12);
        for (int i = 0; i < n; i++) h.append(String.format("%02x", b[i]));
        return h.toString();
    }

    // ---- encoders ----

    public static byte[] heartbeat(long room, long seq) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        wt0(o, 1);
        wv(o, room);
        wt0(o, 2);
        wv(o, seq);
        return o.toByteArray();
    }

    public static byte[] pushFrame(String type, byte[] payload) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        wt2(o, 7);
        wl(o, type.getBytes(StandardCharsets.UTF_8));
        wt2(o, 6);
        wl(o, "pb".getBytes(StandardCharsets.UTF_8));
        wt2(o, 8);
        wl(o, payload);
        return o.toByteArray();
    }

    public static byte[] imEnterRoom(long room) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        wt0(o, 1);
        wv(o, room);
        wt0(o, 4);
        wv(o, 12);
        wt2(o, 5);
        wl(o, "audience".getBytes(StandardCharsets.UTF_8));
        wt2(o, 9);
        wl(o, "0".getBytes(StandardCharsets.UTF_8));
        return o.toByteArray();
    }

    public static byte[] encodeAck(long logId, byte[] internalExt) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        wt0(o, 2);
        wv(o, logId);
        wt2(o, 7);
        wl(o, "ack".getBytes(StandardCharsets.UTF_8));
        wt2(o, 8);
        wl(o, internalExt);
        return o.toByteArray();
    }

    public static byte[] gunzip(byte[] data) {
        try {
            GZIPInputStream g = new GZIPInputStream(new ByteArrayInputStream(data));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = g.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        } catch (Exception e) {
            return data;
        }
    }

    private static void wt0(ByteArrayOutputStream o, int n) {
        o.write(n << 3);
    }

    private static void wt2(ByteArrayOutputStream o, int n) {
        o.write((n << 3) | 2);
    }

    private static void wv(ByteArrayOutputStream o, long v) {
        while ((v & ~0x7f) != 0) {
            o.write((int) ((v & 0x7f) | 0x80));
            v >>>= 7;
        }
        o.write((int) v);
    }

    private static void wl(ByteArrayOutputStream o, byte[] v) {
        wv(o, v.length);
        o.writeBytes(v);
    }

    private static final class PB {
        final byte[] b;
        int p;

        PB(byte[] b) {
            this.b = b;
            p = 0;
        }

        boolean more() {
            return p < b.length;
        }

        long varint() {
            long r = 0;
            int s = 0;
            for (;;) {
                if (p >= b.length) return -1;
                int x = b[p++] & 0xff;
                r |= (long) (x & 0x7f) << s;
                if ((x & 0x80) == 0) break;
                s += 7;
                if (s >= 64) return -1;
            }
            return r;
        }
    }
}