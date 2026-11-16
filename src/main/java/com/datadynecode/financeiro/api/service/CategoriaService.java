package com.datadynecode.financeiro.api.service;

import com.datadynecode.financeiro.api.dto.*;
import com.datadynecode.financeiro.api.exception.*;
import com.datadynecode.financeiro.api.mapper.CategoriaMapper;
import com.datadynecode.financeiro.api.model.Categoria;
import com.datadynecode.financeiro.api.model.TipoLancamento;
import com.datadynecode.financeiro.api.model.Usuario;
import com.datadynecode.financeiro.api.repository.CategoriaRepository;
import com.datadynecode.financeiro.api.repository.LancamentoRepository;
import com.datadynecode.financeiro.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoriaService {
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final LancamentoRepository lancamentoRepository;

    @Transactional
    public CategoriaResponse cadastrar(CategoriaRequest request, String emailAutenticado) {
        Usuario usuario = buscarUsuario(emailAutenticado);
        String nome = request.nome().strip();
        if (categoriaRepository.existsByUsuarioIdAndNomeIgnoreCase(usuario.getId(), nome)) {
            throw new CategoriaEmConflitoException("Ja existe uma categoria com esse nome para o usuario");
        }
        Categoria categoria = new Categoria();
        categoria.setUsuario(usuario);
        categoria.setNome(nome);
        categoria.setTipoLancamento(request.tipoLancamento());
        return CategoriaMapper.toResponse(categoriaRepository.saveAndFlush(categoria));
    }

    @Transactional(readOnly = true)
    public CategoriaPageResponse listar(String emailAutenticado, TipoLancamento tipo, int page, int size) {
        Usuario usuario = buscarUsuario(emailAutenticado);
        PageRequest paginacao = PageRequest.of(page, size, Sort.by("nome", "id"));
        Page<Categoria> categorias = tipo == null
            ? categoriaRepository.findByUsuarioId(usuario.getId(), paginacao)
            : categoriaRepository.findByUsuarioIdAndTipoLancamento(usuario.getId(), tipo, paginacao);
        Page<CategoriaResponse> resultado = categorias.map(CategoriaMapper::toResponse);
        return new CategoriaPageResponse(resultado.getContent(), resultado.getNumber(), resultado.getSize(),
            resultado.getTotalElements(), resultado.getTotalPages());
    }

    @Transactional(readOnly = true)
    public CategoriaResponse buscar(Long id, String emailAutenticado) {
        return CategoriaMapper.toResponse(buscarCategoria(id, emailAutenticado));
    }

    @Transactional
    public CategoriaResponse atualizar(Long id, CategoriaRequest request, String emailAutenticado) {
        Categoria categoria = buscarCategoria(id, emailAutenticado);
        String nome = request.nome().strip();
        if (categoriaRepository.existsByUsuarioIdAndNomeIgnoreCaseAndIdNot(categoria.getUsuario().getId(), nome, id)) {
            throw new CategoriaEmConflitoException("Ja existe uma categoria com esse nome para o usuario");
        }
        if (categoria.getTipoLancamento() != request.tipoLancamento() && lancamentoRepository.existsByCategoriaId(id)) {
            throw new CategoriaEmConflitoException("Nao e possivel alterar o tipo de uma categoria em uso");
        }
        categoria.setNome(nome);
        categoria.setTipoLancamento(request.tipoLancamento());
        return CategoriaMapper.toResponse(categoriaRepository.saveAndFlush(categoria));
    }

    @Transactional
    public void remover(Long id, String emailAutenticado) {
        Categoria categoria = buscarCategoria(id, emailAutenticado);
        if (lancamentoRepository.existsByCategoriaId(id)) {
            throw new CategoriaEmConflitoException("Categoria em uso por lancamentos nao pode ser removida");
        }
        categoriaRepository.delete(categoria);
        categoriaRepository.flush();
    }

    private Usuario buscarUsuario(String emailAutenticado) {
        return usuarioRepository.findByEmail(emailAutenticado)
            .orElseThrow(UsuarioAutenticadoNaoEncontradoException::new);
    }

    private Categoria buscarCategoria(Long id, String emailAutenticado) {
        Usuario usuario = buscarUsuario(emailAutenticado);
        return categoriaRepository.findByIdAndUsuarioId(id, usuario.getId())
            .orElseThrow(CategoriaNaoEncontradaException::new);
    }
}
