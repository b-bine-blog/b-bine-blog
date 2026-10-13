package com.mainblog.blog.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.mainblog.blog.application.BlogService.UpdateCommand;
import com.mainblog.blog.domain.JoinPolicy;
import com.mainblog.blog.domain.Visibility;
import com.mainblog.common.error.InvalidInputException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * PATCH /api/blogs/{blogId} 본문 읽기 (T044, FR-009). 보낸 필드와 보내지 않은 필드, description: null을 나눠야 해서
 * JsonNode로 받는다. 알 수 없는 필드(address 포함)나 빈 본문은 400.
 */
final class BlogUpdateRequests {

    private static final Set<String> FIELDS = Set.of("name", "description", "tags", "visibility", "joinPolicy");
    /** 서버 검사 전에 지나치게 큰 입력을 막는 값. 실제 규칙(이름 30자, 태그 10개 등)은 BlogService가 설정값으로 본다. */
    private static final int MAX_RAW_LENGTH = 2000;
    private static final int MAX_RAW_TAGS = 50;

    private BlogUpdateRequests() {
    }

    static UpdateCommand parse(JsonNode body) {
        if (body == null || !body.isObject() || body.isEmpty()) {
            throw new InvalidInputException("body", "NotEmpty");
        }
        Iterator<String> names = body.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!FIELDS.contains(name)) {
                throw new InvalidInputException(name, "Unknown");
            }
        }
        String name = text(body, "name", false);
        boolean descriptionSet = body.has("description");
        String description = text(body, "description", true);
        List<String> tags = tags(body);
        Visibility visibility = enumValue(body, "visibility", Visibility.class);
        JoinPolicy joinPolicy = enumValue(body, "joinPolicy", JoinPolicy.class);
        return new UpdateCommand(name, descriptionSet, description, tags, visibility, joinPolicy);
    }

    private static String text(JsonNode body, String field, boolean nullable) {
        if (!body.has(field)) {
            return null;
        }
        JsonNode v = body.get(field);
        if (v.isNull() && nullable) {
            return null;
        }
        if (!v.isTextual() || v.asText().length() > MAX_RAW_LENGTH) {
            throw new InvalidInputException(field, "Invalid");
        }
        return v.asText();
    }

    private static List<String> tags(JsonNode body) {
        if (!body.has("tags")) {
            return null;
        }
        JsonNode v = body.get("tags");
        if (!v.isArray() || v.size() > MAX_RAW_TAGS) {
            throw new InvalidInputException("tags", "Invalid");
        }
        List<String> result = new ArrayList<>();
        for (JsonNode t : v) {
            if (!t.isTextual() || t.asText().length() > MAX_RAW_LENGTH) {
                throw new InvalidInputException("tags", "Invalid");
            }
            result.add(t.asText());
        }
        return result;
    }

    private static <E extends Enum<E>> E enumValue(JsonNode body, String field, Class<E> type) {
        if (!body.has(field)) {
            return null;
        }
        JsonNode v = body.get(field);
        try {
            return Enum.valueOf(type, v.asText());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException(field, "Invalid");
        }
    }
}
