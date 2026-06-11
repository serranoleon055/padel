package com.padel.rankpadel.config;

import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.padel.rankpadel.entity.Admin;
import com.padel.rankpadel.repository.AdminRepository;

import lombok.RequiredArgsConstructor;

@Component
@Profile("prod")
@RequiredArgsConstructor
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private static final int MIN_PASSWORD_LENGTH = 8;

    private static final Set<String> SEEDED_DEFAULT_HASHES = Set.of(
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
            "$2a$10$EOJsZmOkvGM4Efum6ojT3Oturyv/vlOestLcGqyKHiK66aoNxVMcy");

    private final AdminRepository adminRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.initial-password:}")
    private String initialPassword;

    @Override
    public void run(String... args) {
        if (initialPassword == null || initialPassword.isBlank()) {
            throw new IllegalStateException(
                    "ADMIN_INITIAL_PASSWORD es obligatoria en producción. "
                            + "Definí la variable de entorno con una contraseña fuerte.");
        }
        if (initialPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "ADMIN_INITIAL_PASSWORD debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres.");
        }

        String username = adminUsername == null ? "admin" : adminUsername.trim();
        Optional<Admin> existente = adminRepository.findByUsername(username);

        if (existente.isEmpty()) {
            Admin admin = new Admin();
            admin.setUsername(username);
            admin.setPasswordHash(passwordEncoder.encode(initialPassword));
            adminRepository.save(admin);
            log.info("[AdminBootstrap] Administrador '{}' creado con la contraseña inicial.", username);
            return;
        }

        Admin admin = existente.get();
        if (SEEDED_DEFAULT_HASHES.contains(admin.getPasswordHash())) {
            admin.setPasswordHash(passwordEncoder.encode(initialPassword));
            adminRepository.save(admin);
            log.warn("[AdminBootstrap] El admin '{}' tenía la contraseña por defecto sembrada; "
                    + "se reemplazó por ADMIN_INITIAL_PASSWORD.", username);
        } else {
            log.info("[AdminBootstrap] El admin '{}' ya tiene una contraseña personalizada; no se modifica.",
                    username);
        }
    }
}
