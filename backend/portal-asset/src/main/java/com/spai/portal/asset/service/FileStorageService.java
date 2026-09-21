package com.spai.portal.asset.service;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.spai.portal.asset.domain.FileObject;
import com.spai.portal.asset.repository.FileObjectRepository;
import com.spai.portal.common.BusinessException;
import java.io.*;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {
    private final GridFsTemplate grid;
    private final FileObjectRepository files;

    public FileStorageService(GridFsTemplate grid, FileObjectRepository files) { this.grid = grid; this.files = files; }

    @Transactional
    public FileObject store(MultipartFile upload) {
        File temp = null;
        try {
            temp = File.createTempFile("portal-upload-", ".tmp");
            upload.transferTo(temp);
            return storeTemp(temp, upload.getOriginalFilename(), upload.getContentType());
        } catch (IOException error) {
            throw failed(error);
        } finally { delete(temp); }
    }

    @Transactional
    public FileObject store(InputStream input, String fileName, String contentType) {
        File temp = null;
        try {
            temp = File.createTempFile("portal-remote-", ".tmp");
            try (OutputStream output = new BufferedOutputStream(new FileOutputStream(temp))) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (count > 0) output.write(buffer, 0, count);
                }
            }
            return storeTemp(temp, fileName, contentType);
        } catch (IOException error) {
            throw failed(error);
        } finally { delete(temp); }
    }

    public StoredFile load(String id) {
        FileObject file = files.findById(id).orElseThrow(() -> BusinessException.notFound("文件不存在"));
        GridFSFile gridFile = grid.findOne(new Query(where("_id").is(new ObjectId(file.getGridfsId()))));
        if (gridFile == null) throw BusinessException.notFound("文件内容不存在");
        return new StoredFile(file, grid.getResource(gridFile));
    }

    private FileObject storeTemp(File temp, String fileName, String contentType) throws IOException {
        String sha = sha(temp);
        Optional<FileObject> old = files.findFirstBySha256AndStatus(sha, "ACTIVE");
        if (old.isPresent()) return old.get();
        ObjectId objectId;
        try (InputStream input = new BufferedInputStream(new FileInputStream(temp))) {
            objectId = grid.store(input, fileName, contentType);
        }
        FileObject file = new FileObject();
        file.setId(UUID.randomUUID().toString());
        file.setGridfsId(objectId.toHexString());
        file.setFileName(fileName == null || fileName.trim().isEmpty() ? "unnamed-file" : fileName);
        file.setContentType(contentType);
        file.setFileSize(temp.length());
        file.setSha256(sha);
        return files.save(file);
    }

    private String sha(File file) throws IOException {
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) > 0) digest.update(buffer, 0, count);
                StringBuilder value = new StringBuilder();
                for (byte item : digest.digest()) value.append(String.format("%02x", item));
                return value.toString();
            } catch (Exception error) { throw new IOException(error); }
        }
    }

    private BusinessException failed(IOException error) {
        return new BusinessException("FILE_STORE_FAILED", "文件存储失败", HttpStatus.INTERNAL_SERVER_ERROR);
    }
    private void delete(File file) { if (file != null && file.exists()) file.delete(); }

    public static class StoredFile {
        public final FileObject metadata;
        public final org.springframework.core.io.Resource resource;
        StoredFile(FileObject metadata, org.springframework.core.io.Resource resource) { this.metadata = metadata; this.resource = resource; }
    }
}
