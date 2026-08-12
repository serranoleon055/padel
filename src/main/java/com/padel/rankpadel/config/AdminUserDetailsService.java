package com.padel.rankpadel.config;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.padel.rankpadel.entity.Admin;
import com.padel.rankpadel.repository.AdminRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminRepository adminRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Admin no encontrado: " + username));

        // ADMIN lo tienen los dos: es "entró al panel", y sobre eso se apoyan casi todas
        // las reglas. El rol propio se suma aparte, para lo que solo puede el dueño.
        return User.builder()
                .username(admin.getUsername())
                .password(admin.getPasswordHash())
                .roles("ADMIN", admin.getRol().name())
                .build();
    }
}