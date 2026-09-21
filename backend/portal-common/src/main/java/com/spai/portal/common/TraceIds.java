package com.spai.portal.common;

import java.util.UUID;

public final class TraceIds {
    private static final ThreadLocal<String> TRACE = new ThreadLocal<String>();
    private TraceIds() {}
    public static String current() { String id = TRACE.get(); return id == null ? "" : id; }
    public static String begin(String requested) { String id = requested == null || requested.trim().isEmpty() ? UUID.randomUUID().toString() : requested; TRACE.set(id); return id; }
    public static void clear() { TRACE.remove(); }
}
