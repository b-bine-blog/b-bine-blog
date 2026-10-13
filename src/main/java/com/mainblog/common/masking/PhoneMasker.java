package com.mainblog.common.masking;

/** 전화번호 가리기 (T011, FR-046, 4.4). 가운데 4자리를 가린다: 01012345678 → 010-****-5678. 서버가 응답을 만들 때 가린다. */
public final class PhoneMasker {

    private PhoneMasker() {
    }

    public static String mask(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 7) {
            return "***";
        }
        return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
    }
}
