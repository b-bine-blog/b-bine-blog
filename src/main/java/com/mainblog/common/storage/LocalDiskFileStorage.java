package com.mainblog.common.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 서버 디스크 저장소 (T059, D-75). 폴더는 FILE_STORAGE_ROOT. */
@Component
public class LocalDiskFileStorage implements FileStorage {

    public static final String URL_PREFIX = "/files/";
    private static final Pattern STORED_NAME = Pattern.compile("^[0-9a-f\\-]{36}\\.[a-z]{3,4}$");

    private final Path root;

    public LocalDiskFileStorage(@Value("${file-storage.root}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public String save(byte[] content, String extension) throws IOException {
        Files.createDirectories(root);
        String storedName = UUID.randomUUID() + "." + extension;
        Files.write(resolve(storedName), content);
        return storedName;
    }

    @Override
    public void delete(String storedName) throws IOException {
        Files.deleteIfExists(resolve(storedName));
    }

    @Override
    public String url(String storedName) {
        return URL_PREFIX + storedName;
    }

    public Path resolve(String storedName) {
        if (!STORED_NAME.matcher(storedName).matches()) {
            throw new IllegalArgumentException("저장 이름 형식이 아닙니다");
        }
        return root.resolve(storedName);
    }

    public Path root() {
        return root;
    }
}
