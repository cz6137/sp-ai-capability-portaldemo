package com.spai.portal.asset.service;

import com.spai.portal.asset.controller.FileController;
import com.spai.portal.common.BusinessException;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileControllerPolicyTest {
    @Test void previewAndDownloadCheckPolicyBeforeReadingFile() {
        FileStorageService files = mock(FileStorageService.class); FileReadPolicy policy = mock(FileReadPolicy.class);
        doThrow(BusinessException.notFound("已下架")).when(policy).checkRead("file-id");
        FileController controller = new FileController(files, Collections.singletonList(policy));
        assertThrows(BusinessException.class, () -> controller.preview("file-id"));
        assertThrows(BusinessException.class, () -> controller.download("file-id"));
        verifyNoInteractions(files);
    }
}
