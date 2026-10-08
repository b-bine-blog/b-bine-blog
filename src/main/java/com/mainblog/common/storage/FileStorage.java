package com.mainblog.common.storage;

import java.io.IOException;

/**
 * 파일 저장소 (T059, D-75, SCL-02). 1차는 서버 디스크, 이중화 때 S3 구현으로 바꾼다.
 * 저장 이름은 UUID로 만들어 원래 이름을 경로에 쓰지 않는다(SEC-08).
 */
public interface FileStorage {

    /** 저장하고 저장 이름(UUID + 확장자)을 돌려준다. */
    String save(byte[] content, String extension) throws IOException;

    void delete(String storedName) throws IOException;

    /** 화면에서 쓸 주소. */
    String url(String storedName);
}
