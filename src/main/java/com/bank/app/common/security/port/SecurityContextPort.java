package com.bank.app.common.security.port;

import java.util.Optional;

/**
 * Application katmanının güvenlik bağlamına erişim portu.
 * Spring Security implementasyon detaylarını soyutlar.
 */
public interface SecurityContextPort {

    Optional<Long> getCurrentUserId();

    Optional<String> getCurrentUsername();

    void checkUserAuthorization(Long resourceUserId, String errorMessage);
}
