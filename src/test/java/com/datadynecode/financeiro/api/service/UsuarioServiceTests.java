package com.datadynecode.financeiro.api.service;

import java.util.Optional;
import java.util.Set;

import com.datadynecode.financeiro.api.dto.UsuarioRequest;
import com.datadynecode.financeiro.api.dto.AlterarSenhaRequest;
import com.datadynecode.financeiro.api.exception.UsuarioAutenticadoNaoEncontradoException;
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
        return new UsuarioRequest("Maria", "Silva", "maria@example.com", senha);
    }

    @Test
    void inativaSemRemoverUsuarioOuDados() {
        Usuario usuario = new Usuario();
        assertTrue(usuario.isEnabled());
        when(usuarios.findById(10L)).thenReturn(Optional.of(usuario));
        service.inativar(10L);
        assertFalse(usuario.isAtivo());
        assertFalse(usuario.isEnabled());
        service.inativar(10L);
        verify(usuarios, times(2)).saveAndFlush(usuario);
        verify(usuarios, never()).delete(any());
        assertThrows(com.datadynecode.financeiro.api.exception.UsuarioNaoEncontradoException.class,
            () -> service.inativar(99L));
    }

    @Test
    void alteraSenhaSomenteAposConferirAtual() {
        Usuario usuario = new Usuario();
        usuario.setSenha("hash-antigo");
        when(usuarios.findByEmail("maria@example.com")).thenReturn(Optional.of(usuario));
        when(encoder.matches("SenhaAtual123!", "hash-antigo")).thenReturn(true);
        when(encoder.encode("NovaSenha123!")).thenReturn("hash-novo");
        service.alterarSenha(new AlterarSenhaRequest("SenhaAtual123!", "NovaSenha123!"), "maria@example.com");
        assertEquals("hash-novo", usuario.getSenha());
        verify(usuarios).saveAndFlush(usuario);
        verifyNoInteractions(roles);
    }

    @Test
    void senhaAtualIncorretaPreservaHash() {
        Usuario usuario = new Usuario();
        usuario.setSenha("hash-antigo");
        when(usuarios.findByEmail("maria@example.com")).thenReturn(Optional.of(usuario));
        assertThrows(CadastroInvalidoException.class, () -> service.alterarSenha(
            new AlterarSenhaRequest("errada", "NovaSenha123!"), "maria@example.com"));
        assertEquals("hash-antigo", usuario.getSenha());
        verify(usuarios, never()).saveAndFlush(any());
        verify(encoder, never()).encode(any());
    }

    @Test
    void trocaDeSenhaExigeUsuarioExistenteELimiteEmBytes() {
        var request = new AlterarSenhaRequest("SenhaAtual123!", "á".repeat(40));
        assertThrows(UsuarioAutenticadoNaoEncontradoException.class, () -> service.alterarSenha(request, "ausente@example.com"));
        when(usuarios.findByEmail("maria@example.com")).thenReturn(Optional.of(new Usuario()));
        assertThrows(CadastroInvalidoException.class, () -> service.alterarSenha(request, "maria@example.com"));
        verify(usuarios, never()).saveAndFlush(any());
        verifyNoInteractions(encoder);
    }

    @Test
    void cadastraComHashERoleExistente() {
        Role role = new Role();
        role.setAuthority("ROLE_USER");
        when(roles.findByAuthority("ROLE_USER")).thenReturn(Optional.of(role));
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
        assertTrue(captor.getValue().getRoles().contemPorNome("ROLE_USER"));
        assertEquals(10L, response.id());
        assertEquals(Set.of("ROLE_USER"), response.roles());
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
        when(roles.findByAuthority("ROLE_USER")).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> service.cadastrar(request("SenhaForte123!")));
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
