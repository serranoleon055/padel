package com.padel.rankpadel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.padel.rankpadel.dto.request.AdminRequest;
import com.padel.rankpadel.dto.response.AdminResponse;
import com.padel.rankpadel.entity.Admin;
import com.padel.rankpadel.enums.RolUsuario;
import com.padel.rankpadel.exception.EstadoInvalidoException;
import com.padel.rankpadel.repository.AdminRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService - roles de usuario")
class AdminServiceTest {

    @Mock
    private AdminRepository adminRepository;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("hash");
    }

    private Admin admin(Long id, String username, RolUsuario rol) {
        Admin admin = new Admin();
        admin.setId(id);
        admin.setUsername(username);
        admin.setPasswordHash("hash");
        admin.setRol(rol);
        return admin;
    }

    private AdminRequest pedido(String username, String password, RolUsuario rol) {
        AdminRequest request = new AdminRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setRol(rol);
        return request;
    }

    @Test
    @DisplayName("Un usuario nuevo sin rol explícito queda como dueño")
    void crear_sinRol_quedaDuenio() {
        when(adminRepository.existsByUsername("nuevo")).thenReturn(false);
        when(adminRepository.save(any(Admin.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminResponse respuesta = adminService.crear(pedido("nuevo", "clave-larga-1", null));

        assertThat(respuesta.getRol()).isEqualTo("DUENIO");
    }

    @Test
    @DisplayName("Se puede crear un usuario de mostrador")
    void crear_mostrador() {
        when(adminRepository.existsByUsername("mostrador")).thenReturn(false);
        when(adminRepository.save(any(Admin.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminResponse respuesta = adminService.crear(pedido("mostrador", "clave-larga-1", RolUsuario.MOSTRADOR));

        assertThat(respuesta.getRol()).isEqualTo("MOSTRADOR");
    }

    @Test
    @DisplayName("Bajar al último dueño a mostrador se rechaza: nadie podría administrar el club")
    void actualizar_ultimoDuenio_rechaza() {
        Admin unico = admin(1L, "leon", RolUsuario.DUENIO);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(unico));
        when(adminRepository.findByUsername("leon")).thenReturn(Optional.of(unico));
        when(adminRepository.findAll()).thenReturn(List.of(unico));

        assertThatThrownBy(() -> adminService.actualizar(1L, pedido("leon", null, RolUsuario.MOSTRADOR)))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessageContaining("al menos un usuario dueño");
        assertThat(unico.getRol()).isEqualTo(RolUsuario.DUENIO);
    }

    @Test
    @DisplayName("Si queda otro dueño, sí se puede bajar a uno a mostrador")
    void actualizar_quedaOtroDuenio_permite() {
        Admin uno = admin(1L, "leon", RolUsuario.DUENIO);
        Admin dos = admin(2L, "matias", RolUsuario.DUENIO);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(uno));
        when(adminRepository.findByUsername("leon")).thenReturn(Optional.of(uno));
        when(adminRepository.findAll()).thenReturn(List.of(uno, dos));
        when(adminRepository.save(any(Admin.class))).thenAnswer(inv -> inv.getArgument(0));

        adminService.actualizar(1L, pedido("leon", null, RolUsuario.MOSTRADOR));

        assertThat(uno.getRol()).isEqualTo(RolUsuario.MOSTRADOR);
    }

    @Test
    @DisplayName("Borrar al último dueño se rechaza aunque queden usuarios de mostrador")
    void eliminar_ultimoDuenio_rechaza() {
        Admin duenio = admin(1L, "leon", RolUsuario.DUENIO);
        Admin mostrador = admin(2L, "mostrador", RolUsuario.MOSTRADOR);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(duenio));
        when(adminRepository.count()).thenReturn(2L);
        when(adminRepository.findAll()).thenReturn(List.of(duenio, mostrador));

        assertThatThrownBy(() -> adminService.eliminar(1L))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessageContaining("al menos un usuario dueño");
        verify(adminRepository, never()).delete(any());
    }
}
