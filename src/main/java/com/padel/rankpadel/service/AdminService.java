package com.padel.rankpadel.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.padel.rankpadel.dto.request.AdminRequest;
import com.padel.rankpadel.dto.response.AdminResponse;
import com.padel.rankpadel.entity.Admin;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.exception.ResourceNotFoundException;
import com.padel.rankpadel.repository.AdminRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminRepository adminRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<AdminResponse> listarTodos() {
        return adminRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(Admin::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdminResponse crear(AdminRequest request) {
        String username = normalizeUsername(request.getUsername());
        String password = normalizePassword(request.getPassword());

        if (password == null) {
            throw new EstadoInvalidoException("La contraseña es obligatoria.");
        }
        if (adminRepository.existsByUsername(username)) {
            throw new EstadoInvalidoException("Ya existe un administrador con ese usuario.");
        }

        Admin admin = new Admin();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(password));

        return toResponse(adminRepository.save(admin));
    }

    @Transactional
    public AdminResponse actualizar(Long id, AdminRequest request) {
        Admin admin = adminRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Admin", id));

        String username = normalizeUsername(request.getUsername());
        adminRepository.findByUsername(username)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new EstadoInvalidoException("Ya existe un administrador con ese usuario.");
                });

        admin.setUsername(username);
        String password = normalizePassword(request.getPassword());
        if (password != null) {
            admin.setPasswordHash(passwordEncoder.encode(password));
        }

        return toResponse(adminRepository.save(admin));
    }

    @Transactional
    public void eliminar(Long id) {
        Admin admin = adminRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Admin", id));

        if (adminRepository.count() <= 1) {
            throw new EstadoInvalidoException("No se puede eliminar el ultimo administrador.");
        }

        adminRepository.delete(admin);
    }

    private AdminResponse toResponse(Admin admin) {
        return AdminResponse.builder()
                .id(admin.getId())
                .username(admin.getUsername())
                .build();
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private static final int MIN_PASSWORD_LENGTH = 10;

    private String normalizePassword(String password) {
        if (password == null || password.isBlank()) {
            return null;
        }
        String normalizada = password.trim();
        if (normalizada.length() < MIN_PASSWORD_LENGTH) {
            throw new EstadoInvalidoException(
                    "La contraseña debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres.");
        }
        return normalizada;
    }
}
