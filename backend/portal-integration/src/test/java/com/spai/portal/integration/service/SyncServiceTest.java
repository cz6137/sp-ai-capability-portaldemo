package com.spai.portal.integration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.spai.portal.asset.domain.Asset;
import com.spai.portal.asset.domain.AssetCategory;
import com.spai.portal.asset.domain.CaseCategory;
import com.spai.portal.asset.domain.FileObject;
import com.spai.portal.asset.repository.AssetCategoryRepository;
import com.spai.portal.asset.repository.AssetRepository;
import com.spai.portal.asset.repository.CaseCategoryRepository;
import com.spai.portal.asset.service.FileStorageService;
import com.spai.portal.integration.domain.SyncJob;
import com.spai.portal.integration.repository.SyncJobRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

class SyncServiceTest {
    private HttpServer server;

    @AfterEach
    void stopServer() { if (server != null) server.stop(0); }

    @Test
    void synchronizesImaAssetIdempotentlyAndDownloadsToGridFs() throws Exception {
        AtomicInteger listCalls = new AtomicInteger();
        AtomicInteger missingHeaders = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/openapi/wiki/v1/get_knowledge_list", json(exchange -> {
            checkHeaders(exchange, missingHeaders);
            int call = listCalls.getAndIncrement() % 3;
            if (call == 0) return response("stage-folder", "0-项目监控", 99);
            if (call == 1) return response("category-folder", "01-项目周报", 99);
            return response("media-1", "0 - T - 01 - 项目周报模板.docx", 1);
        }));
        server.createContext("/openapi/wiki/v1/get_media_info", json(exchange -> {
            checkHeaders(exchange, missingHeaders);
            return "{\"code\":0,\"data\":{\"url_info\":{\"url\":\"http://127.0.0.1:" + server.getAddress().getPort() + "/file\"}}}";
        }));
        server.createContext("/file", exchange -> send(exchange, "application/octet-stream", "abc"));
        server.start();

        SyncJobRepository jobs = mock(SyncJobRepository.class);
        AssetRepository assets = mock(AssetRepository.class);
        AssetCategoryRepository categories = mock(AssetCategoryRepository.class);
        CaseCategoryRepository caseCategories = mock(CaseCategoryRepository.class);
        FileStorageService files = mock(FileStorageService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AtomicReference<Asset> stored = new AtomicReference<Asset>();

        when(jobs.save(any(SyncJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), anyLong())).thenReturn(Boolean.TRUE);
        when(categories.findByExternalFolderId(anyString())).thenReturn(Optional.empty());
        when(categories.findFirstByStageIdAndName(any(Integer.class), anyString())).thenReturn(Optional.empty());
        when(categories.save(any(AssetCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assets.findByMediaId("media-1")).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(assets.save(any(Asset.class))).thenAnswer(invocation -> { Asset value = invocation.getArgument(0); stored.set(value); return value; });
        when(assets.findBySourceType("IMA_SERVER")).thenReturn(Collections.<Asset>emptyList());
        FileObject file = new FileObject(); file.setId("file-1");
        when(files.store(any(InputStream.class), eq("项目周报模板.docx"), eq("application/octet-stream"))).thenReturn(file);

        SyncService service = new SyncService(jobs, assets, categories, caseCategories, files, jdbc, new RestTemplateBuilder(),
            true, "http://127.0.0.1:" + server.getAddress().getPort(), "client", "key", "knowledge");
        SyncJob first = service.trigger("IMA");
        String firstId = stored.get().getId();
        SyncJob second = service.trigger("IMA");

        assertEquals("SUCCESS", first.getStatus());
        assertEquals("SUCCESS", second.getStatus());
        assertEquals(firstId, stored.get().getId());
        assertEquals("file-1", stored.get().getCurrentFileId());
        assertEquals(Integer.valueOf(0), stored.get().getStageId());
        assertEquals("T", stored.get().getType());
        assertFalse(firstId.isEmpty());
        assertEquals(0, missingHeaders.get());
    }

    @Test
    void synchronizesMaintainableBenchmarkCaseFolderByBusinessCategory() throws Exception {
        AtomicInteger listCalls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/openapi/wiki/v1/get_knowledge_list", json(exchange -> {
            int call = listCalls.getAndIncrement();
            if (call == 0) return response("case-root", "标杆案例归纳", 99);
            if (call == 1) return response("business-folder", "不动产管理", 99);
            return response("case-media-1", "8 - C - 01 - 广州市不动产交付总结.docx", 1);
        }));
        server.createContext("/openapi/wiki/v1/get_media_info", json(exchange ->
            "{\"code\":0,\"data\":{\"url_info\":{\"url\":\"http://127.0.0.1:" + server.getAddress().getPort() + "/case-file\"}}}"));
        server.createContext("/case-file", exchange -> send(exchange, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "case"));
        server.start();

        SyncJobRepository jobs = mock(SyncJobRepository.class);
        AssetRepository assets = mock(AssetRepository.class);
        AssetCategoryRepository categories = mock(AssetCategoryRepository.class);
        CaseCategoryRepository caseCategories = mock(CaseCategoryRepository.class);
        FileStorageService files = mock(FileStorageService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AtomicReference<CaseCategory> business = new AtomicReference<CaseCategory>();
        AtomicReference<Asset> stored = new AtomicReference<Asset>();

        when(jobs.save(any(SyncJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), anyLong())).thenReturn(Boolean.TRUE);
        when(categories.findByExternalFolderId(anyString())).thenReturn(Optional.empty());
        when(categories.findFirstByStageIdAndName(any(Integer.class), anyString())).thenReturn(Optional.empty());
        when(categories.save(any(AssetCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(caseCategories.findByExternalFolderId(anyString())).thenReturn(Optional.empty());
        when(caseCategories.findByName("不动产管理")).thenReturn(Optional.empty());
        when(caseCategories.save(any(CaseCategory.class))).thenAnswer(invocation -> { CaseCategory value = invocation.getArgument(0); business.set(value); return value; });
        when(assets.findByMediaId("case-media-1")).thenReturn(Optional.empty());
        when(assets.save(any(Asset.class))).thenAnswer(invocation -> { Asset value = invocation.getArgument(0); stored.set(value); return value; });
        when(assets.findBySourceType("IMA_SERVER")).thenReturn(Collections.<Asset>emptyList());
        FileObject file = new FileObject(); file.setId("case-file-1");
        when(files.store(any(InputStream.class), eq("广州市不动产交付总结.docx"),
            eq("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))).thenReturn(file);

        SyncService service = new SyncService(jobs, assets, categories, caseCategories, files, jdbc, new RestTemplateBuilder(),
            true, "http://127.0.0.1:" + server.getAddress().getPort(), "client", "key", "knowledge");
        SyncJob job = service.trigger("IMA");

        assertEquals("SUCCESS", job.getStatus());
        assertEquals("不动产管理", business.get().getName());
        assertEquals("business-folder", business.get().getExternalFolderId());
        assertEquals("C", stored.get().getType());
        assertEquals(business.get().getId(), stored.get().getCaseCategoryId());
        assertEquals("case-file-1", stored.get().getCurrentFileId());
    }

    private static HttpHandler json(final Responder responder) {
        return exchange -> send(exchange, "application/json;charset=UTF-8", responder.respond(exchange));
    }

    private static String response(String id, String title, int mediaType) {
        return "{\"code\":0,\"data\":{\"knowledge_list\":[{\"media_id\":\"" + id + "\",\"title\":\"" + title
            + "\",\"media_type\":" + mediaType + "}],\"is_end\":true}}";
    }

    private static void checkHeaders(HttpExchange exchange, AtomicInteger missing) {
        if (!"client".equals(exchange.getRequestHeaders().getFirst("ima-openapi-clientid"))) missing.incrementAndGet();
        if (!"key".equals(exchange.getRequestHeaders().getFirst("ima-openapi-apikey"))) missing.incrementAndGet();
    }

    private static void send(HttpExchange exchange, String contentType, String value) throws IOException {
        byte[] body = value.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(body); }
    }

    private interface Responder { String respond(HttpExchange exchange) throws IOException; }
}
