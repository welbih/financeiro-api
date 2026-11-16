package com.datadynecode.financeiro.api.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.datadynecode.financeiro.api.dto.LancamentoRequest;
import com.datadynecode.financeiro.api.dto.LancamentoResponse;
import com.datadynecode.financeiro.api.dto.LancamentoPageResponse;
import com.datadynecode.financeiro.api.dto.ResumoMensalLancamentosResponse;
import com.datadynecode.financeiro.api.exception.CadastroInvalidoException;
import com.datadynecode.financeiro.api.exception.UsuarioAutenticadoNaoEncontradoException;
import com.datadynecode.financeiro.api.exception.LancamentoNaoEncontradoException;
import com.datadynecode.financeiro.api.mapper.LancamentoMapper;
import com.datadynecode.financeiro.api.model.Lancamento;
import com.datadynecode.financeiro.api.model.Usuario;
import com.datadynecode.financeiro.api.model.TipoLancamento;
import com.datadynecode.financeiro.api.repository.LancamentoRepository;
import com.datadynecode.financeiro.api.repository.UsuarioRepository;
import com.datadynecode.financeiro.api.repository.CategoriaRepository;
import com.datadynecode.financeiro.api.model.Categoria;
import com.datadynecode.financeiro.api.exception.CategoriaNaoEncontradaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LancamentoService {
    private final LancamentoRepository lancamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CategoriaRepository categoriaRepository;

    @Transactional
    public LancamentoResponse cadastrar(LancamentoRequest request, String emailAutenticado) {
        Usuario usuario = buscarUsuario(emailAutenticado);
        Lancamento lancamento = new Lancamento();
        lancamento.setUsuario(usuario);
        aplicarDados(lancamento, request);
        lancamentoRepository.saveAndFlush(lancamento);
        int quantidade = request.quantidadeRecorrencias() == null ? 0 : request.quantidadeRecorrencias();
        for (int mes = 1; mes <= quantidade; mes++) {
            Lancamento recorrente = new Lancamento();
            recorrente.setUsuario(usuario);
            recorrente.setCategoria(lancamento.getCategoria());
            recorrente.setTipoLancamento(lancamento.getTipoLancamento());
            recorrente.setDescricao(lancamento.getDescricao());
            recorrente.setValor(lancamento.getValor());
            recorrente.setDataVencimento(lancamento.getDataVencimento().plusMonths(mes));
            if (lancamento.getDataPrevisaoPagamento() != null) {
                recorrente.setDataPrevisaoPagamento(lancamento.getDataPrevisaoPagamento().plusMonths(mes));
            }
            if (lancamento.isPago()) {
                recorrente.pagarEm(lancamento.getDataPagamento());
            }
            lancamentoRepository.save(recorrente);
        }
        return LancamentoMapper.toResponse(lancamento);
    }

    @Transactional(readOnly = true)
    public LancamentoPageResponse listar(String emailAutenticado, int page, int size) {
        Usuario usuario = buscarUsuario(emailAutenticado);
        PageRequest paginacao = PageRequest.of(page, size, Sort.by("dataVencimento", "id"));
        Page<LancamentoResponse> resultado = lancamentoRepository.findByUsuarioId(usuario.getId(), paginacao)
            .map(LancamentoMapper::toResponse);
        return new LancamentoPageResponse(resultado.getContent(), resultado.getNumber(), resultado.getSize(),
            resultado.getTotalElements(), resultado.getTotalPages());
    }

    @Transactional
    public LancamentoResponse atualizar(Long id, LancamentoRequest request, String emailAutenticado) {
        if (request.quantidadeRecorrencias() != null && request.quantidadeRecorrencias() != 0) {
            throw new CadastroInvalidoException("Recorrencias podem ser geradas somente no cadastro");
        }
        Lancamento lancamento = buscarLancamento(id, emailAutenticado);
        aplicarDados(lancamento, request);
        return LancamentoMapper.toResponse(lancamentoRepository.saveAndFlush(lancamento));
    }

    @Transactional(readOnly = true)
    public ResumoMensalLancamentosResponse resumirMes(String emailAutenticado, YearMonth mes) {
        Usuario usuario = buscarUsuario(emailAutenticado);
        List<Lancamento> lancamentos = lancamentoRepository.findPorPeriodo(
            usuario.getId(), mes.atDay(1), mes.atEndOfMonth());
        BigDecimal despesas = BigDecimal.ZERO.setScale(2);
        BigDecimal receitas = BigDecimal.ZERO.setScale(2);
        for (Lancamento lancamento : lancamentos) {
            if (lancamento.getTipoLancamento() == TipoLancamento.DESPESA) {
                despesas = despesas.add(lancamento.getValor());
            }
            else {
                receitas = receitas.add(lancamento.getValor());
            }
        }
        List<LancamentoResponse> registros = lancamentos.stream().map(LancamentoMapper::toResponse).toList();
        return new ResumoMensalLancamentosResponse(mes.toString(), despesas, receitas,
            receitas.subtract(despesas), registros);
    }

    @Transactional
    public LancamentoResponse pagar(Long id, LocalDate data, String emailAutenticado) {
        Lancamento lancamento = buscarLancamento(id, emailAutenticado);
        validarDataPagamento(data);
        if (lancamento.isPago()) {
            throw new CadastroInvalidoException("Lancamento ja esta pago");
        }
        lancamento.pagarEm(data == null ? LocalDate.now(ZoneId.of("America/Sao_Paulo")) : data);
        return LancamentoMapper.toResponse(lancamentoRepository.saveAndFlush(lancamento));
    }

    @Transactional
    public void remover(Long id, String emailAutenticado) {
        Lancamento lancamento = buscarLancamento(id, emailAutenticado);
        lancamentoRepository.delete(lancamento);
    }

    private Usuario buscarUsuario(String emailAutenticado) {
        return usuarioRepository.findByEmail(emailAutenticado)
            .orElseThrow(UsuarioAutenticadoNaoEncontradoException::new);
    }

    private Lancamento buscarLancamento(Long id, String emailAutenticado) {
        Usuario usuario = buscarUsuario(emailAutenticado);
        return lancamentoRepository.findByIdAndUsuarioId(id, usuario.getId())
            .orElseThrow(LancamentoNaoEncontradoException::new);
    }

    private void aplicarDados(Lancamento lancamento, LancamentoRequest request) {
        validarPagamento(request);
        Categoria categoria = buscarCategoria(request, lancamento.getUsuario());
        lancamento.setCategoria(categoria);
        lancamento.setTipoLancamento(request.tipoLancamento());
        lancamento.setDescricao(request.descricao().strip());
        lancamento.setValor(request.valor());
        lancamento.setDataVencimento(request.dataVencimento());
        lancamento.setDataPrevisaoPagamento(request.dataPrevisaoPagamento());
        lancamento.reabrirPagamento();
        if (Boolean.TRUE.equals(request.pago())) {
            lancamento.pagarEm(request.dataPagamento());
        }
    }

    private Categoria buscarCategoria(LancamentoRequest request, Usuario usuario) {
        if (request.categoriaId() == null) {
            return null;
        }
        Categoria categoria = categoriaRepository.findByIdAndUsuarioId(request.categoriaId(), usuario.getId())
            .orElseThrow(CategoriaNaoEncontradaException::new);
        if (categoria.getTipoLancamento() != request.tipoLancamento()) {
            throw new CadastroInvalidoException("Tipo da categoria deve ser igual ao tipo do lancamento");
        }
        return categoria;
    }

    private void validarPagamento(LancamentoRequest request) {
        validarDataPagamento(request.dataPagamento());
        boolean pago = Boolean.TRUE.equals(request.pago());
        boolean possuiDataPagamento = request.dataPagamento() != null;
        if (pago != possuiDataPagamento) {
            throw new CadastroInvalidoException("Data de pagamento deve ser informada somente quando pago for true");
        }
    }

    private void validarDataPagamento(LocalDate data) {
        if (data != null && data.isAfter(LocalDate.now(ZoneId.of("America/Sao_Paulo")))) {
            throw new CadastroInvalidoException("Data de pagamento nao pode ser futura");
        }
    }
}
