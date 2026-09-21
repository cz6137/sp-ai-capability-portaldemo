package com.spai.portal.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.spai.portal.asset.domain.FileObject;
import com.spai.portal.asset.repository.FileObjectRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

class FileStorageServiceTest {
    @Test
    void storesStreamThatTemporarilyReturnsZeroBytes() {
        GridFsTemplate grid = mock(GridFsTemplate.class);
        FileObjectRepository repository = mock(FileObjectRepository.class);
        when(repository.findFirstBySha256AndStatus(any(String.class), eq("ACTIVE"))).thenReturn(Optional.empty());
        when(grid.store(any(InputStream.class), eq("skill.zip"), eq("application/zip"))).thenReturn(new ObjectId());
        when(repository.save(any(FileObject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FileObject result = new FileStorageService(grid, repository)
            .store(new ZeroThenDataInputStream(), "skill.zip", "application/zip");

        assertEquals(3L, result.getFileSize());
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", result.getSha256());
    }

    private static class ZeroThenDataInputStream extends InputStream {
        private final byte[] data = new byte[] { 'a', 'b', 'c' };
        private int position = -1;

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (position < 0) { position = 0; return 0; }
            if (position >= data.length) return -1;
            int count = Math.min(length, data.length - position);
            System.arraycopy(data, position, buffer, offset, count);
            position += count;
            return count;
        }

        @Override
        public int read() throws IOException {
            if (position < 0) position = 0;
            return position >= data.length ? -1 : data[position++];
        }
    }
}
