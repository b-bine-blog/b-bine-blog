package com.mainblog.auth.oauth;

import java.util.List;
import java.util.Optional;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/** 키가 있어 쓸 수 있는 소셜 서비스 목록 (008 FR-014). 하나도 없으면 소셜 로그인을 켜지 않는다. */
public class SocialClients {

    private final List<ClientRegistration> registrations;

    public SocialClients(List<ClientRegistration> registrations) {
        this.registrations = List.copyOf(registrations);
    }

    public boolean any() {
        return !registrations.isEmpty();
    }

    public List<SocialProvider> providers() {
        return registrations.stream()
                .map(r -> SocialProvider.fromRegistrationId(r.getRegistrationId()))
                .flatMap(Optional::stream)
                .toList();
    }

    public boolean has(SocialProvider provider) {
        return providers().contains(provider);
    }

    public ClientRegistrationRepository repository() {
        return new InMemoryClientRegistrationRepository(registrations);
    }
}
