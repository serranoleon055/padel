package com.padel.rankpadel.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.padel.rankpadel.dto.request.LoginRequest;
import com.padel.rankpadel.dto.response.LoginResponse;
import com.padel.rankpadel.entity.Admin;
import com.padel.rankpadel.exception.CredencialesInvalidasException;
import com.padel.rankpadel.repository.AdminRepository;
import com.padel.rankpadel.util.JwtUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AdminRepository adminRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername().trim();
        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new CredencialesInvalidasException("Credenciales invalidas"));

        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            throw new CredencialesInvalidasException("Credenciales invalidas");
        }

        String token = jwtUtil.generateToken(admin.getUsername());

        LoginResponse respuesta = new LoginResponse();
        respuesta.setToken(token);
        return respuesta;
    }
}
