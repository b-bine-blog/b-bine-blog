package com.mainblog.rules.api;

import com.mainblog.common.config.AdminProperties;
import com.mainblog.common.config.AuthProperties;
import com.mainblog.common.config.BlogProperties;
import com.mainblog.common.config.BoardProperties;
import com.mainblog.common.config.InquiryProperties;
import com.mainblog.common.config.MemberProperties;
import com.mainblog.common.config.NotificationProperties;
import com.mainblog.common.config.SocialProperties;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영 규칙 화면이 보여 줄 숫자 (D-114, specs/007-site-rules/contracts/site-rules-api.yaml).
 * 화면에 숫자를 따로 적지 않고 실제 설정값(D-68)을 그대로 내려 주어, 설정을 바꾸면 안내도 같이 바뀐다.
 * 비밀 값과 내부 운영값(요청 제한 창, 관리자 조회 한도 등)은 넣지 않는다.
 */
@RestController
public class SiteRulesController {

    public record Account(long idleMinutes, long rememberDays, int captchaAfter, int lockAfter, long lockMinutes,
                          long signupCodeMinutes, long resetCodeMinutes, int codeMaxFail, long withdrawalDays) {}

    public record Blog(int publicLimit, int privateLimit, int closingGraceDays, int closingHour,
                       List<Integer> closingNoticeDays, long closedPurgeDays, long joinReapplyDays,
                       long delegationExpireDays, int sanctionKickCount, long sanctionWindowDays) {}

    public record Board(int titleMaxLength, int contentMaxLength, int commentMaxLength, int tagMaxCount,
                        long imageMaxMegabytes, int imageMaxPerPost, long deletedPurgeDays) {}

    public record Report(long repeatAfterDays) {}

    public record Notification(int defaultDays, int shortDays) {}

    public record Admin(int warningRevokeThreshold, long warningWindowDays, long recordsRetentionDays) {}

    public record Inquiry(int rateLimit, long rateWindowHours, long retentionDays) {}

    public record SiteRules(Account account, Blog blog, Board board, Report report, Notification notification,
                            Admin admin, Inquiry inquiry) {}

    private final SiteRules rules;

    public SiteRulesController(AuthProperties auth, MemberProperties member, BlogProperties blog,
                               BoardProperties board, SocialProperties social, NotificationProperties notification,
                               AdminProperties admin, InquiryProperties inquiry) {
        this.rules = new SiteRules(
                new Account(auth.idleTimeout().toMinutes(), auth.rememberMeTtl().toDays(),
                        auth.login().captchaAfter(), auth.login().lockAfter(), auth.login().lockDuration().toMinutes(),
                        auth.signup().codeTtl().toMinutes(), auth.reset().codeTtl().toMinutes(),
                        auth.code().maxFail(), member.withdrawal().retention().toDays()),
                new Blog(blog.limit().publicLimit(), blog.limit().privateLimit(), blog.closing().graceDays(),
                        blog.closing().hour(), List.copyOf(blog.closing().noticeDays()),
                        blog.closing().purgeAfter().toDays(), blog.join().reapplyAfter().toDays(),
                        blog.delegation().expireAfter().toDays(), blog.sanction().kickSuggestCount(),
                        blog.sanction().countWindow().toDays()),
                new Board(board.post().titleMaxLength(), board.post().contentMaxLength(),
                        board.comment().maxLength(), board.tag().maxCount(), board.image().maxSize().toMegabytes(),
                        board.image().maxPerPost(), board.purgeAfter().toDays()),
                new Report(social.report().repeatAfter().toDays()),
                new Notification(notification.retention().defaultDays(), notification.retention().shortDays()),
                new Admin(admin.warning().revokeThreshold(), admin.warning().window().toDays(),
                        admin.records().retention().toDays()),
                new Inquiry(inquiry.rateLimit(), inquiry.rateWindow().toHours(), inquiry.retention().toDays()));
    }

    @GetMapping("/api/site-rules")
    public SiteRules rules() {
        return rules;
    }
}
