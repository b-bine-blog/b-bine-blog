package com.mainblog.common.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 인증 메일 3종 (T018, D-10, D-69). Gmail SMTP로 텍스트 메일을 보낸다.
 * 본문에는 인증번호 외의 개인정보를 넣지 않는다(R-08).
 */
@Component
public class AuthMailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public AuthMailSender(JavaMailSender mailSender, @Value("${spring.mail.username:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    /** 가입 인증번호. 10분 동안 유효(USR-02). */
    @Async
    public void sendSignupCode(String to, String code) {
        send(to, "[Blbine Blog] 회원가입 인증번호",
                "회원가입 인증번호는 " + code + " 입니다.\n"
                        + "10분 안에 입력해 주세요.\n"
                        + "직접 요청하지 않았다면 이 메일을 무시하세요.");
    }

    /** 이미 가입된 이메일로 가입을 시도했을 때(FR-006). 화면 응답은 인증번호 발송과 같다. */
    @Async
    public void sendAlreadyRegistered(String to) {
        send(to, "[Blbine Blog] 이미 가입된 이메일입니다",
                "이 이메일은 이미 Blbine Blog에 가입되어 있습니다.\n"
                        + "비밀번호가 기억나지 않으면 로그인 화면의 \"비밀번호 찾기\"를 이용해 주세요.\n"
                        + "직접 요청하지 않았다면 이 메일을 무시하세요.");
    }

    /** 비밀번호 재설정 인증번호. 30분 동안 유효(SEC-05). */
    @Async
    public void sendResetCode(String to, String code) {
        send(to, "[Blbine Blog] 비밀번호 재설정 인증번호",
                "비밀번호 재설정 인증번호는 " + code + " 입니다.\n"
                        + "30분 안에 입력해 주세요.\n"
                        + "직접 요청하지 않았다면 이 메일을 무시하세요. 비밀번호는 바뀌지 않습니다.");
    }

    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (!from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
