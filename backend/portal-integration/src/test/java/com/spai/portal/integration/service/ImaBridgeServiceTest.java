package com.spai.portal.integration.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spai.portal.asset.domain.*;
import com.spai.portal.asset.repository.*;
import com.spai.portal.common.BusinessException;
import com.spai.portal.integration.domain.*;
import com.spai.portal.integration.dto.ImaBridgeDtos.*;
import com.spai.portal.integration.repository.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImaBridgeServiceTest {
    private SyncJobRepository jobs;
    private ImaBridgeRunItemRepository runItems;
    private AssetRepository assets;
    private AssetCategoryRepository categories;
    private CaseCategoryRepository caseCategories;
    private FileObjectRepository files;
    private ImaBridgeService service;
    private SyncJob run;

    @BeforeEach
    void setup() {
        jobs = mock(SyncJobRepository.class);
        runItems = mock(ImaBridgeRunItemRepository.class);
        assets = mock(AssetRepository.class);
        categories = mock(AssetCategoryRepository.class);
        caseCategories = mock(CaseCategoryRepository.class);
        files = mock(FileObjectRepository.class);
        service = new ImaBridgeService(jobs, runItems, assets, categories, caseCategories, files, new ObjectMapper());
        run = new SyncJob(); run.setId("run-1"); run.setSource("IMA_BRIDGE"); run.setStatus("RUNNING");
        run.setSourceShareId("share value"); run.setStartedAt(OffsetDateTime.now());
        when(jobs.findById("run-1")).thenReturn(Optional.of(run));
        when(jobs.save(any(SyncJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void upsertsTheSameMediaIdWithoutChangingAssetIdentity() {
        AtomicReference<Asset> stored = new AtomicReference<Asset>();
        AtomicReference<ImaBridgeRunItem> marker = new AtomicReference<ImaBridgeRunItem>();
        when(assets.findByMediaId("media/1")).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(assets.save(any(Asset.class))).thenAnswer(invocation -> { Asset value = invocation.getArgument(0); stored.set(value); return value; });
        when(categories.findByExternalFolderId("folder-1")).thenReturn(Optional.empty());
        when(categories.findFirstByStageIdAndName(0, "项目周报")).thenReturn(Optional.empty());
        when(categories.save(any(AssetCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(runItems.findByRunIdAndMediaId("run-1", "media/1")).thenAnswer(invocation -> Optional.ofNullable(marker.get()));
        when(runItems.save(any(ImaBridgeRunItem.class))).thenAnswer(invocation -> { ImaBridgeRunItem value = invocation.getArgument(0); marker.set(value); return value; });
        when(runItems.countByRunId("run-1")).thenReturn(1L);

        BatchRequest request = request();
        BatchResult first = service.upsert("run-1", request);
        String assetId = stored.get().getId();
        BatchResult second = service.upsert("run-1", request);

        assertEquals(assetId, stored.get().getId());
        assertEquals("IMA_BRIDGE", stored.get().getSourceType());
        assertEquals("run-1", stored.get().getLastSyncRunId());
        assertEquals("https://ima.qq.com/wiki/?shareId=share+value&action=autoOpenMedia&mediaId=media%2F1", stored.get().getSourceUrl());
        assertEquals(1, first.created);
        assertEquals(1, second.updated);
        verify(runItems, times(1)).save(any(ImaBridgeRunItem.class));
    }

    @Test
    void failedRunDoesNotDisableExistingAssets() {
        FailRequest request = new FailRequest(); request.message = "network interrupted";
        SyncJob result = service.fail("run-1", request);
        assertEquals("FAILED", result.getStatus());
        verify(assets, never()).disableMissingImaBridgeAssets(anyString());
    }

    @Test
    void successfulCompletionDisablesOnlyMissingBridgeAssets() {
        when(runItems.countByRunId("run-1")).thenReturn(2L);
        when(assets.disableMissingImaBridgeAssets("run-1")).thenReturn(1);
        SyncJob result = service.complete("run-1");
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(2, result.getSuccessCount());
        verify(assets).disableMissingImaBridgeAssets("run-1");
    }

    @Test
    void emptyRunCannotBeCompleted() {
        when(runItems.countByRunId("run-1")).thenReturn(0L);
        assertThrows(BusinessException.class, () -> service.complete("run-1"));
        verify(assets, never()).disableMissingImaBridgeAssets(anyString());
    }

    @Test
    void bindsAnUploadedGridFsFileToTheBridgeAsset() {
        AtomicReference<Asset> stored = new AtomicReference<Asset>();
        when(assets.findByMediaId("media/1")).thenReturn(Optional.empty());
        when(assets.save(any(Asset.class))).thenAnswer(invocation -> { Asset value = invocation.getArgument(0); stored.set(value); return value; });
        when(categories.findByExternalFolderId("folder-1")).thenReturn(Optional.empty());
        when(categories.findFirstByStageIdAndName(0, "项目周报")).thenReturn(Optional.empty());
        when(categories.save(any(AssetCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(runItems.findByRunIdAndMediaId("run-1", "media/1")).thenReturn(Optional.empty());
        when(runItems.save(any(ImaBridgeRunItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(runItems.countByRunId("run-1")).thenReturn(1L);
        FileObject file = new FileObject(); file.setId("gridfs-file-1"); file.setStatus("ACTIVE");
        when(files.findById("gridfs-file-1")).thenReturn(Optional.of(file));

        BatchRequest request = request(); request.items.get(0).fileId = "gridfs-file-1";
        service.upsert("run-1", request);

        assertEquals("gridfs-file-1", stored.get().getCurrentFileId());
    }

    private BatchRequest request() {
        Item item = new Item(); item.mediaId = "media/1"; item.name = "项目周报模板.docx"; item.type = "T";
        item.stageId = 0; item.categoryName = "项目周报"; item.categoryNumber = "01"; item.folderId = "folder-1";
        item.metadata.put("updated_at", "2026-08-21T00:00:00Z");
        BatchRequest request = new BatchRequest(); request.items.add(item); return request;
    }
}
