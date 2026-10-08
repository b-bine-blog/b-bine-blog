package com.mainblog.member.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAgreementRepository extends JpaRepository<UserAgreement, Long> {

    List<UserAgreement> findAllByUserId(Long userId);
}
