package com.datadynecode.financeiro.api.service;

import java.util.Optional;

import com.datadynecode.financeiro.api.model.Role;
import com.datadynecode.financeiro.api.model.Usuario;
import com.datadynecode.financeiro.api.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsuarioDetailsServiceTests {

    private final UsuarioRepository repository = mock(UsuarioRepository.class);
    private final UsuarioDetailsService service = new UsuarioDetailsService(repository);

    @Test
    void carregaCredenciaisEAuthoritiesPeloEmail() {
        Usuario usuario = new Usuario();
        usuario.setEmail("admin@financeiro.local");
        usuario.setSenha("hash-da-senha");
        Role role = new Role();
        role.setAuthority("ROLE_ADMIN");
        usuario.getRoles().adicionar(role);
        when(repository.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));

        UserDetails details = service.loadUserByUsername(usuario.getEmail());

        assertEquals("admin@financeiro.local", details.getUsername());
        assertEquals("hash-da-senha", details.getPassword());
        assertEquals(1, details.getAuthorities().size());
        assertEquals("ROLE_ADMIN", details.getAuthorities().iterator().next().getAuthority());
        assertThrows(UnsupportedOperationException.class, () -> details.getAuthorities().clear());
        assertTrue(details.isEnabled());
        assertTrue(details.isAccountNonLocked());
        assertTrue(details.isAccountNonExpired());
        assertTrue(details.isCredentialsNonExpired());
    }

    @Test
    void rejeitaEmailNaoCadastrado() {
        when(repository.findByEmail("ausente@financeiro.local")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
            () -> service.loadUserByUsername("ausente@financeiro.local"));
    }
}
