package com.mainblog.member.domain;

import com.mainblog.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 회원(users). 규칙은 data-model.md "users".
 * login_fail_count, locked_until은 login_failures로 옮겨 매핑하지 않는다(R-05, D-97).
 */
@Entity
@Table(name = "users")
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소문자. 탈퇴 즉시 NULL(FR-047). */
    @Column(name = "email")
    private String email;

    @Column(name = "withdrawn_email")
    private String withdrawnEmail;
    /** 소셜로만 가입하면 NULL(008 FR-009, D-125). */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "name")
    private String name;

    @Column(name = "nickname")
    private String nickname;

    @Column(name = "phone")
    private String phone;

    @Column(name = "bio")
    private String bio;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    protected User() {
    }

    /** 가입은 항상 USER, ACTIVE(SEC-09). 값은 MemberInputNormalizer·정책을 거친 뒤 넘긴다. */
    public static User signUp(String email, String passwordHash, String name, String nickname, String phone) {
        User u = new User();
        u.email = email;
        u.passwordHash = passwordHash;
        u.name = name;
        u.nickname = nickname;
        u.phone = phone;
        u.role = UserRole.USER;
        u.status = UserStatus.ACTIVE;
        return u;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changePhone(String phone) {
        this.phone = phone;
    }

    /** 탈퇴: 이메일을 30일 보관 칸으로 옮기고 비운다. 닉네임은 30일 동안 그대로 둔다(FR-047, FR-048, D-97). */
    public void withdraw(LocalDateTime now) {
        this.withdrawnEmail = this.email;
        this.email = null;
        this.status = UserStatus.WITHDRAWN;
        this.withdrawnAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getWithdrawnEmail() {
        return withdrawnEmail;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public String getNickname() {
        return nickname;
    }

    public String getPhone() {
        return phone;
    }

    public String getBio() {
        return bio;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public LocalDateTime getWithdrawnAt() {
        return withdrawnAt;
    }
}
