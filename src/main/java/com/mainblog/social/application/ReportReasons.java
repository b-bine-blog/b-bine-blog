package com.mainblog.social.application;

import com.mainblog.common.config.SocialProperties;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 신고 사유 목록 (003 T035, FR-022, D-102 #14). 설정값 social.report.*를 그대로 읽어 화면과 서버가 같은 목록을 쓴다. */
@Component
public class ReportReasons {

    private final SocialProperties props;

    public ReportReasons(SocialProperties props) {
        this.props = props;
    }

    public record Reason(String code, String label, boolean detailRequired) {}

    public record Reasons(List<Reason> reasons, int detailMaxLength) {}

    public Reasons list() {
        Map<String, String> labels = props.report().reasonLabels();
        List<Reason> items = labels.entrySet().stream()
                .map(e -> new Reason(e.getKey(), e.getValue(), detailRequired(e.getKey()))).toList();
        return new Reasons(items, props.report().detailMaxLength());
    }

    public boolean exists(String code) {
        return code != null && props.report().reasonLabels().containsKey(code);
    }

    public boolean detailRequired(String code) {
        return props.report().detailRequired().stream().anyMatch(c -> c.strip().equals(code));
    }

    public int detailMaxLength() {
        return props.report().detailMaxLength();
    }
}
