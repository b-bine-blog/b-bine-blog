package com.mainblog.auth.application;

import com.mainblog.auth.domain.PasswordResetToken;
import com.mainblog.auth.domain.PasswordResetTokenRepository;
import com.mainblog.auth.security.CodeHasher;
import com.mainblog.member.domain.User;
import com.mainblog.member.domain.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** password_reset_tokens로 대상 회원을 찾는다(T066, FR-038). 만료된 토큰은 찾을 때 바로 지운다. */
@Component
public class DbResetTokenResolver implements ResetTokenResolver {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final CodeHasher codeHasher;
    private final Clock clock;

    public DbResetTokenResolver(PasswordResetTokenRepository tokenRepository, UserRepository userRepository,
                                CodeHasher codeHasher, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.codeHasher = codeHasher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<User> resolve(String rawToken) {
        Optional<PasswordResetToken> token = find(rawToken);
        if (token.isEmpty()) {
            return Optional.empty();
        }
        if (token.get().isExpired(LocalDateTime.now(clock))) {
            tokenRepository.delete(token.get());
            return Optional.empty();
        }
        return userRepository.findById(token.get().getUserId()).filter(User::isActive);
    }

    @Override
    @Transactional
    public void consume(String rawToken) {
        find(rawToken).ifPresent(tokenRepository::delete);
    }

    private Optional<PasswordResetToken> find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return tokenRepository.findByTokenHash(codeHasher.sha256(rawToken));
    }
}
