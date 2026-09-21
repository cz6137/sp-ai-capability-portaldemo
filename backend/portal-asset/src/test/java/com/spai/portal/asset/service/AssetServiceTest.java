package com.spai.portal.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class AssetServiceTest {
    @Test
    void classifiesBenchmarkCasesByBusinessDomain() {
        assertEquals("10000000-0000-0000-0000-000000000001", AssetService.classifyCase("广州市不动产登记项目总结"));
        assertEquals("10000000-0000-0000-0000-000000000002", AssetService.classifyCase("城市大脑统一 GIS 平台方案"));
        assertEquals("10000000-0000-0000-0000-000000000005", AssetService.classifyCase("综合项目复盘"));
    }
}
