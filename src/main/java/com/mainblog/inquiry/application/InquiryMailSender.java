package com.mainblog.inquiry.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 비회원 시스템 문의 답변 메일 (ADM-08, D-112). Gmail SMTP로 텍스트 메일을 보낸다(D-10).
 * 본문에는 문의 제목·내용과 답변만 넣는다. 보내지 못해도 답변은 저장된 채로 두고 로그에는 주소를 남기지 않는다(D-22).
 */
@Component
public class InquiryMailSender {

    private static final Logger log = LoggerFactory.getLogger(InquiryMailSender.class);

    private final JavaMailSender mailSender;
    private final String from;

    public InquiryMailSender(JavaMailSender mailSender, @Value("${spring.mail.username:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Async
    public void sendAnswer(String to, long inquiryId, String title, String content, String answer) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (!from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(to);
        message.setSubject("[b-bine blog] 시스템 문의에 답변드립니다");
        message.setText("보내 주신 시스템 문의에 답변드립니다.\n\n"
                + "[문의] " + title + "\n" + content + "\n\n"
                + "[답변]\n" + answer + "\n\n"
                + "이 메일은 보내기 전용입니다. 더 궁금한 점은 사이트 아래 \"시스템 문의\"로 다시 보내 주세요.");
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("시스템 문의 #{} 답변 메일을 보내지 못했습니다: {}", inquiryId, e.getClass().getSimpleName());
        }
    }
}
