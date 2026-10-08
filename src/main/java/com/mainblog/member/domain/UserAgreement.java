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

/** 가입 동의 기록(user_agreements). (user_id, agreement_type) UNIQUE (FR-014). */
@Entity
@Table(name = "user_agreements")
public class UserAgreement extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "agreement_type", nullable = false)
    private AgreementType agreementType;

    @Column(name = "agreed_at", nullable = false)
    private LocalDateTime agreedAt;

    protected UserAgreement() {
    }

    public UserAgreement(Long userId, AgreementType agreementType, LocalDateTime agreedAt) {
        this.userId = userId;
        this.agreementType = agreementType;
        this.agreedAt = agreedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public AgreementType getAgreementType() {
        return agreementType;
    }

    public LocalDateTime getAgreedAt() {
        return agreedAt;
    }
}
