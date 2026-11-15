package com.datadynecode.financeiro.api.service;

import java.util.List;
import java.util.Set;

import com.datadynecode.financeiro.api.dto.UsuarioRequest;
import com.datadynecode.financeiro.api.exception.CadastroInvalidoException;
import com.datadynecode.financeiro.api.exception.EmailJaCadastradoException;
import com.datadynecode.financeiro.api.model.Role;
import com.datadynecode.financeiro.api.model.Usuario;
import com.datadynecode.financeiro.api.repository.RoleRepository;
import com.datadynecode.financeiro.api.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsuarioServiceTests {
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final UsuarioService service = new UsuarioService(usuarios, roles, encoder);

    private UsuarioRequest request(String senha) {
        return new UsuarioRequest("Maria", "Silva", "maria@example.com", senha, Set.of("ROLE_ADMIN"));
    }

    @Test
    void cadastraComHashERoleExistente() {
        Role role = new Role();
        role.setAuthority("ROLE_ADMIN");
        when(roles.findByAuthorityIn(Set.of("ROLE_ADMIN"))).thenReturn(List.of(role));
        when(encoder.encode("SenhaForte123!")).thenReturn("hash-bcrypt");
        when(usuarios.saveAndFlush(any())).thenAnswer(invocation -> {
            Usuario usuario = invocation.getArgument(0);
            usuario.setId(10L);
            return usuario;
        });

        var response = service.cadastrar(request("SenhaForte123!"));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).saveAndFlush(captor.capture());
        assertEquals("hash-bcrypt", captor.getValue().getSenha());
        assertTrue(captor.getValue().getRoles().contemPorNome("ROLE_ADMIN"));
        assertEquals(10L, response.id());
        assertEquals(Set.of("ROLE_ADMIN"), response.roles());
    }

    @Test
    void rejeitaEmailDuplicado() {
        when(usuarios.existsByEmail("maria@example.com")).thenReturn(true);
        assertThrows(EmailJaCadastradoException.class, () -> service.cadastrar(request("SenhaForte123!")));
        verify(usuarios, never()).saveAndFlush(any());
        verifyNoInteractions(encoder, roles);
    }

    @Test
    void rejeitaRoleInexistente() {
        when(roles.findByAuthorityIn(any())).thenReturn(List.of());
        assertThrows(CadastroInvalidoException.class, () -> service.cadastrar(request("SenhaForte123!")));
        verify(usuarios, never()).saveAndFlush(any());
        verifyNoInteractions(encoder);
    }

    @Test
    void rejeitaSenhaMaiorQueLimiteBcryptEmBytes() {
        assertThrows(CadastroInvalidoException.class, () -> service.cadastrar(request("á".repeat(40))));
        verify(usuarios, never()).saveAndFlush(any());
        verifyNoInteractions(encoder, roles);
    }
}
