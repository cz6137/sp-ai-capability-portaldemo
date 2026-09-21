package com.spai.portal.asset.service;

/** Domain modules can protect their files on the legacy generic download/preview routes. */
public interface FileReadPolicy {
    void checkRead(String fileId);
}
