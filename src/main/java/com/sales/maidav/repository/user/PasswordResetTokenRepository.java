package com.sales.maidav.repository.user;

import com.sales.maidav.model.user.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNull(String tokenHash);
    List<PasswordResetToken> findByUser_IdAndUsedAtIsNull(Long userId);
}
