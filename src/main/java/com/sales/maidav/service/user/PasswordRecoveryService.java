package com.sales.maidav.service.user;

import com.sales.maidav.model.user.PasswordResetToken;
import com.sales.maidav.model.user.User;
import com.sales.maidav.repository.user.PasswordResetTokenRepository;
import com.sales.maidav.repository.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class PasswordRecoveryService {

    private static final int TOKEN_LENGTH = 8;
    private static final int EXPIRATION_MINUTES = 15;
    private static final String TOKEN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public RecoveryTokenResult generateRecoveryToken(String identifier) {
        User user = findUserByIdentifier(identifier);
        return generateRecoveryToken(user);
    }

    public RecoveryTokenResult generateRecoveryTokenForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        return generateRecoveryToken(user);
    }

    public RecoveryTokenResult generateRecoveryTokenForUser(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        return generateRecoveryToken(user);
    }

    private RecoveryTokenResult generateRecoveryToken(User user) {
        invalidateUnusedTokens(user);

        String token = generateToken();
        PasswordResetToken passwordResetToken = new PasswordResetToken();
        passwordResetToken.setUser(user);
        passwordResetToken.setTokenHash(hashToken(token));
        passwordResetToken.setExpiresAt(LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES));
        passwordResetTokenRepository.save(passwordResetToken);

        return new RecoveryTokenResult(
                token,
                passwordResetToken.getExpiresAt(),
                buildUserLabel(user)
        );
    }

    public void resetPassword(String token, String newPassword, String confirmPassword) {
        String normalizedToken = normalizeToken(token);
        if (normalizedToken == null) {
            throw new IllegalArgumentException("El token es obligatorio");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("La nueva contraseña es obligatoria");
        }
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 6 caracteres");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("La confirmacion de contraseña no coincide");
        }

        PasswordResetToken passwordResetToken = passwordResetTokenRepository
                .findByTokenHashAndUsedAtIsNull(hashToken(normalizedToken))
                .orElseThrow(() -> new IllegalArgumentException("El token no es valido o ya fue usado"));

        if (passwordResetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("El token ya vencio");
        }

        User user = passwordResetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        passwordResetToken.setUsedAt(LocalDateTime.now());
        invalidateUnusedTokens(user);
    }

    private User findUserByIdentifier(String identifier) {
        String normalized = identifier == null ? "" : identifier.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Debe ingresar email o usuario");
        }

        return userRepository.findByEmailIgnoreCase(normalized)
                .orElseGet(() -> findByEmailLocalPart(normalized)
                        .orElseThrow(() -> new IllegalArgumentException("No existe un usuario con ese identificador")));
    }

    private java.util.Optional<User> findByEmailLocalPart(String identifier) {
        String normalized = identifier.trim().toLowerCase(Locale.ROOT);
        List<User> matches = userRepository.findAll().stream()
                .filter(user -> user.getEmail() != null)
                .filter(user -> {
                    String email = user.getEmail().trim().toLowerCase(Locale.ROOT);
                    int separatorIndex = email.indexOf('@');
                    String localPart = separatorIndex >= 0 ? email.substring(0, separatorIndex) : email;
                    return localPart.equals(normalized);
                })
                .toList();

        if (matches.isEmpty()) {
            return java.util.Optional.empty();
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("El usuario es ambiguo. Ingrese el email completo");
        }
        return java.util.Optional.of(matches.getFirst());
    }

    private void invalidateUnusedTokens(User user) {
        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenRepository.findByUser_IdAndUsedAtIsNull(user.getId())
                .forEach(token -> token.setUsedAt(now));
    }

    private String buildUserLabel(User user) {
        String fullName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        if (!fullName.isBlank()) {
            return fullName;
        }
        return user.getEmail();
    }

    private String generateToken() {
        StringBuilder tokenBuilder = new StringBuilder(TOKEN_LENGTH);
        for (int index = 0; index < TOKEN_LENGTH; index++) {
            tokenBuilder.append(TOKEN_CHARS.charAt(secureRandom.nextInt(TOKEN_CHARS.length())));
        }
        return tokenBuilder.toString();
    }

    private String normalizeToken(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim().replace(" ", "").toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("No se pudo generar el hash del token", ex);
        }
    }

    public record RecoveryTokenResult(String token, LocalDateTime expiresAt, String userLabel) {
    }
}
