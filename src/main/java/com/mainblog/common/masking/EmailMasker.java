package com.mainblog.common.masking;

/**
 * 이메일 가리기 (T067, FR-036, 4.4, D-22). @ 앞 처음 2글자만 보이고 나머지는 {@code ***}.
 * @ 앞이 2글자 이하면 첫 글자만 보인다. 서버가 응답을 만들 때 가린다.
 */
public final class EmailMasker {

    private static final String MASK = "***";

    private EmailMasker() {
    }

    public static String mask(String email) {
        if (email == null) {
            return null;
        }
        int at = email.lastIndexOf('@');
        if (at <= 0) {
            return MASK;
        }
        String local = email.substring(0, at);
        int visible = local.length() <= 2 ? 1 : 2;
        return local.substring(0, visible) + MASK + email.substring(at);
    }
}
