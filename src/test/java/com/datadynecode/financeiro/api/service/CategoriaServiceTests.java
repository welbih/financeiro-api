package com.datadynecode.financeiro.api.service;

import java.util.List;
import java.util.Optional;

import com.datadynecode.financeiro.api.dto.CategoriaRequest;
import com.datadynecode.financeiro.api.exception.CategoriaEmConflitoException;
import com.datadynecode.financeiro.api.exception.CategoriaNaoEncontradaException;
import com.datadynecode.financeiro.api.model.Categoria;
import com.datadynecode.financeiro.api.model.TipoLancamento;
import com.datadynecode.financeiro.api.model.Usuario;
import com.datadynecode.financeiro.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CategoriaServiceTests {
    private final CategoriaRepository categorias = mock(CategoriaRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final LancamentoRepository lancamentos = mock(LancamentoRepository.class);
    private final CategoriaService service = new CategoriaService(categorias, usuarios, lancamentos);
    private final Usuario usuario = new Usuario();
    private final Categoria categoria = new Categoria();

    @BeforeEach
    void setup() {
        usuario.setId(10L);
        categoria.setId(30L);
        categoria.setNome("Moradia");
        categoria.setTipoLancamento(TipoLancamento.DESPESA);
        categoria.setUsuario(usuario);
        when(usuarios.findByEmail("dono@example.com")).thenReturn(Optional.of(usuario));
        when(categorias.findByIdAndUsuarioId(30L, 10L)).thenReturn(Optional.of(categoria));
        when(categorias.saveAndFlush(any())).thenAnswer(invocation -> {
            Categoria saved = invocation.getArgument(0);
            saved.setId(30L);
            return saved;
        });
    }

    @Test
    void cadastraComNomeLimpoEUsuarioAutenticado() {
        var response = service.cadastrar(new CategoriaRequest(" Moradia ", TipoLancamento.DESPESA), "dono@example.com");
        assertEquals("Moradia", response.nome());
        assertEquals(10L, response.usuarioId());
        verify(categorias).existsByUsuarioIdAndNomeIgnoreCase(10L, "Moradia");
    }

    @Test
    void rejeitaNomeDuplicadoIndependentementeDoTipo() {
        when(categorias.existsByUsuarioIdAndNomeIgnoreCase(10L, "moradia")).thenReturn(true);
        assertThrows(CategoriaEmConflitoException.class, () -> service.cadastrar(
            new CategoriaRequest(" moradia ", TipoLancamento.RECEITA), "dono@example.com"));
        verify(categorias, never()).saveAndFlush(any());
    }

    @Test
    void listaSomenteTipoEUsuarioSolicitados() {
        PageRequest page = PageRequest.of(0, 20, Sort.by("nome", "id"));
        when(categorias.findByUsuarioIdAndTipoLancamento(10L, TipoLancamento.DESPESA, page))
            .thenReturn(new PageImpl<>(List.of(categoria), page, 1));
        var response = service.listar("dono@example.com", TipoLancamento.DESPESA, 0, 20);
        assertEquals(1, response.totalElements());
        assertEquals(10L, response.content().getFirst().usuarioId());
        verify(categorias, never()).findByUsuarioId(any(), any());
    }

    @Test
    void atualizaNomeSemConfundirComPropriaCategoria() {
        var response = service.atualizar(30L, new CategoriaRequest(" Moradia ", TipoLancamento.DESPESA), "dono@example.com");
        assertEquals("Moradia", response.nome());
        verify(categorias).existsByUsuarioIdAndNomeIgnoreCaseAndIdNot(10L, "Moradia", 30L);
    }

    @Test
    void rejeitaRenomearParaNomeDeOutraCategoria() {
        when(categorias.existsByUsuarioIdAndNomeIgnoreCaseAndIdNot(10L, "Lazer", 30L)).thenReturn(true);
        assertThrows(CategoriaEmConflitoException.class, () -> service.atualizar(
            30L, new CategoriaRequest("Lazer", TipoLancamento.DESPESA), "dono@example.com"));
        verify(categorias, never()).saveAndFlush(any());
    }

    @Test
    void naoPermiteTrocarTipoQuandoHaLancamentos() {
        when(lancamentos.existsByCategoriaId(30L)).thenReturn(true);
        assertThrows(CategoriaEmConflitoException.class, () -> service.atualizar(
            30L, new CategoriaRequest("Moradia", TipoLancamento.RECEITA), "dono@example.com"));
        assertEquals(TipoLancamento.DESPESA, categoria.getTipoLancamento());
    }

    @Test
    void permiteRenomearCategoriaUsadaSemTrocarTipo() {
        var response = service.atualizar(30L, new CategoriaRequest("Casa", TipoLancamento.DESPESA), "dono@example.com");
        assertEquals("Casa", response.nome());
    }

    @Test
    void bloqueiaRemocaoDeCategoriaUsada() {
        when(lancamentos.existsByCategoriaId(30L)).thenReturn(true);
        assertThrows(CategoriaEmConflitoException.class, () -> service.remover(30L, "dono@example.com"));
        verify(categorias, never()).delete(any());
        verify(lancamentos, never()).delete(any());
    }

    @Test
    void removeCategoriaLivreSemApagarLancamentos() {
        service.remover(30L, "dono@example.com");
        verify(categorias).delete(categoria);
        verify(lancamentos, never()).delete(any());
    }

    @Test
    void bloqueiaAcessoAtualizacaoERemocaoDeCategoriaAlheia() {
        assertThrows(CategoriaNaoEncontradaException.class, () -> service.buscar(99L, "dono@example.com"));
        assertThrows(CategoriaNaoEncontradaException.class, () -> service.atualizar(
            99L, new CategoriaRequest("Casa", TipoLancamento.DESPESA), "dono@example.com"));
        assertThrows(CategoriaNaoEncontradaException.class, () -> service.remover(99L, "dono@example.com"));
        verify(categorias, never()).saveAndFlush(any());
        verify(categorias, never()).delete(any());
    }
}
