package com.datadynecode.financeiro.api.service;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.datadynecode.financeiro.api.dto.UsuarioRequest;
import com.datadynecode.financeiro.api.dto.UsuarioResponse;
import com.datadynecode.financeiro.api.exception.CadastroInvalidoException;
import com.datadynecode.financeiro.api.exception.EmailJaCadastradoException;
import com.datadynecode.financeiro.api.mapper.UsuarioMapper;
import com.datadynecode.financeiro.api.model.Role;
import com.datadynecode.financeiro.api.model.Usuario;
import com.datadynecode.financeiro.api.repository.RoleRepository;
import com.datadynecode.financeiro.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioService {
    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UsuarioResponse cadastrar(UsuarioRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new EmailJaCadastradoException();
        }
        if (request.senha().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new CadastroInvalidoException("Senha deve ter no maximo 72 bytes em UTF-8");
        }
        List<Role> roles = roleRepository.findByAuthorityIn(request.roles());
        if (roles.size() != request.roles().size()) {
            throw new CadastroInvalidoException("Uma ou mais permissoes nao estao cadastradas");
        }
        Usuario usuario = new Usuario();
        usuario.setNome(request.nome());
        usuario.setSobrenome(request.sobrenome());
        usuario.setEmail(request.email());
        usuario.setSenha(passwordEncoder.encode(request.senha()));
        roles.forEach(usuario.getRoles()::adicionar);
        return UsuarioMapper.toResponse(usuarioRepository.saveAndFlush(usuario));
    }
}
