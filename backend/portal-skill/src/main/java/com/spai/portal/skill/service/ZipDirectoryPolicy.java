package com.spai.portal.skill.service;

import com.spai.portal.common.BusinessException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Fail-closed ZIP32 central-directory checks, without extracting uploaded files. */
final class ZipDirectoryPolicy {
    static void validate(File file) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            long length = in.length();
            long end = -1;
            for (long p = length - 22; p >= Math.max(0, length - 65557); p--) {
                in.seek(p);
                if (u32(in) == 0x06054b50L) {
                    in.seek(p + 20);
                    if (p + 22 + u16(in) == length) { end = p; break; }
                }
            }
            check(end >= 0, "ZIP 目录结束标记无效");
            in.seek(end + 4);
            check(u16(in) == 0 && u16(in) == 0, "不接受分卷 ZIP");
            int diskCount = u16(in), count = u16(in);
            long size = u32(in), start = u32(in);
            check(count == diskCount && count > 0 && count <= 10000, "ZIP 条目数量无效或超过限制");
            check(size != 0xffffffffL && start != 0xffffffffL && start + size == end, "不接受 ZIP64 或异常目录");
            long cursor = start;
            for (int i = 0; i < count; i++) {
                check(cursor + 46 <= end, "ZIP 目录截断");
                in.seek(cursor);
                check(u32(in) == 0x02014b50L, "ZIP 目录条目无效");
                in.seek(cursor + 8);
                int flags = u16(in), method = u16(in);
                check((flags & 0x2041) == 0, "不接受加密 ZIP");
                check(method == 0 || method == 8, "ZIP 压缩方式不受支持");
                in.seek(cursor + 28);
                int name = u16(in), extra = u16(in), comment = u16(in), disk = u16(in);
                u16(in);
                long attributes = u32(in), local = u32(in);
                check(attributes != 0xffffffffL, "不接受 ZIP64 或异常目录");
                // Java 8 的 ZipEntry 没有 getExternalAttributes（Java 9 才加入），必须从中央目录原始字节读取 Unix 文件类型。
                int type = (int) (attributes >>> 16) & 0170000;
                check(type == 0 || type == 0100000 || type == 0040000, "ZIP 禁止链接和特殊文件");
                check(disk == 0 && local + 30 <= start, "ZIP 本地条目位置无效");
                in.seek(local);
                check(u32(in) == 0x04034b50L, "ZIP 本地条目无效");
                in.seek(local + 6);
                check(u16(in) == flags && u16(in) == method, "ZIP 本地与中央目录标记不一致");
                cursor += 46L + name + extra + comment;
                check(cursor <= end, "ZIP 目录长度无效");
            }
            check(cursor == end, "ZIP 目录条目数量不一致");
        }
    }
    private static int u16(RandomAccessFile in) throws IOException {
        return in.readUnsignedByte() | (in.readUnsignedByte() << 8);
    }
    private static long u32(RandomAccessFile in) throws IOException {
        return u16(in) | ((long) u16(in) << 16);
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw BusinessException.badRequest(message);
    }
}
