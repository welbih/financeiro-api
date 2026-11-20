package com.datadynecode.financeiro.api.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Clock;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;

import com.datadynecode.financeiro.api.dto.*;
import com.datadynecode.financeiro.api.exception.CadastroInvalidoException;
import com.datadynecode.financeiro.api.exception.PlanejamentoNaoEncontradoException;
import com.datadynecode.financeiro.api.exception.UsuarioAutenticadoNaoEncontradoException;
import com.datadynecode.financeiro.api.mapper.LancamentoMapper;
import com.datadynecode.financeiro.api.mapper.PlanejamentoFinanceiroMapper;
import com.datadynecode.financeiro.api.model.Lancamento;
import com.datadynecode.financeiro.api.model.PlanejamentoFinanceiro;
import com.datadynecode.financeiro.api.model.TipoLancamento;
import com.datadynecode.financeiro.api.model.Usuario;
import com.datadynecode.financeiro.api.repository.LancamentoRepository;
import com.datadynecode.financeiro.api.repository.PlanejamentoFinanceiroRepository;
import com.datadynecode.financeiro.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlanejamentoFinanceiroService {
    private final PlanejamentoFinanceiroRepository planejamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final LancamentoRepository lancamentoRepository;
    private final Clock clock;

    @Transactional
    public PlanejamentoFinanceiroResponse configurar(PlanejamentoFinanceiroRequest request, String emailAutenticado) {
        Usuario usuario = buscarUsuario(emailAutenticado);
        LocalDate diaAnterior = request.dataInicioControle().minusDays(1);
        if (!diaAnterior.equals(fechamento(YearMonth.from(diaAnterior), request.diaFechamento()))) {
            throw new CadastroInvalidoException("Inicio do controle deve ser o dia seguinte a um fechamento");
        }
        PlanejamentoFinanceiro planejamento = planejamentoRepository.findByUsuarioId(usuario.getId())
            .orElseGet(PlanejamentoFinanceiro::new);
        planejamento.setUsuario(usuario);
        planejamento.setDiaFechamento(request.diaFechamento());
        planejamento.setDataInicioControle(request.dataInicioControle());
        planejamento.setSaldoInicial(request.saldoInicial().setScale(2));
        return respostaConfiguracao(planejamentoRepository.saveAndFlush(planejamento));
    }

    @Transactional(readOnly = true)
    public PlanejamentoFinanceiroResponse buscar(String emailAutenticado) {
        Usuario usuario = buscarUsuario(emailAutenticado);
        return respostaConfiguracao(buscarPlanejamento(usuario));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResumoPlanejamentoFinanceiroResponse resumir(String emailAutenticado, YearMonth mesFechamento) {
        Usuario usuario = buscarUsuario(emailAutenticado);
        PlanejamentoFinanceiro planejamento = buscarPlanejamento(usuario);
        LocalDate fim = fechamento(mesFechamento, planejamento.getDiaFechamento());
        LocalDate inicio = fechamento(mesFechamento.minusMonths(1), planejamento.getDiaFechamento()).plusDays(1);
        if (inicio.isBefore(planejamento.getDataInicioControle())) {
            throw new CadastroInvalidoException("Ciclo solicitado e anterior ao inicio do controle");
        }
        BigDecimal saldoInicial = planejamento.getSaldoInicial();
        if (inicio.isAfter(planejamento.getDataInicioControle())) {
            saldoInicial = saldoInicial.add(lancamentoRepository.calcularResultadoEntre(
                usuario.getId(), planejamento.getDataInicioControle(), inicio, TipoLancamento.RECEITA));
        }
        List<Lancamento> lancamentos = lancamentoRepository.findPorPeriodo(usuario.getId(), inicio, fim);
        BigDecimal receitas = BigDecimal.ZERO.setScale(2);
        BigDecimal despesas = BigDecimal.ZERO.setScale(2);
        BigDecimal receitasRecebidas = BigDecimal.ZERO.setScale(2);
        BigDecimal despesasPagas = BigDecimal.ZERO.setScale(2);
        for (Lancamento lancamento : lancamentos) {
            if (lancamento.getTipoLancamento() == TipoLancamento.RECEITA) {
                receitas = receitas.add(lancamento.getValor());
                if (lancamento.isPago()) {
                    receitasRecebidas = receitasRecebidas.add(lancamento.getValor());
                }
            }
            else {
                despesas = despesas.add(lancamento.getValor());
                if (lancamento.isPago()) {
                    despesasPagas = despesasPagas.add(lancamento.getValor());
                }
            }
        }
        BigDecimal resultado = receitas.subtract(despesas);
        return new ResumoPlanejamentoFinanceiroResponse(mesFechamento.toString(), inicio, fim, saldoInicial,
            receitas, despesas, receitasRecebidas, receitas.subtract(receitasRecebidas), despesasPagas,
            despesas.subtract(despesasPagas), resultado, saldoInicial.add(resultado),
            lancamentos.stream().map(LancamentoMapper::toResponse).toList(), agruparPorCategoria(lancamentos));
    }

    private List<TotalPorCategoriaResponse> agruparPorCategoria(List<Lancamento> lancamentos) {
        Map<CategoriaTipo, TotalPorCategoriaResponse> totais = new HashMap<>();
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        for (Lancamento lancamento : lancamentos) {
            var categoria = lancamento.getCategoria();
            Long id = categoria == null ? null : categoria.getId();
            String nome = categoria == null ? "Sem categoria" : categoria.getNome();
            TipoLancamento tipo = lancamento.getTipoLancamento();
            CategoriaTipo chave = new CategoriaTipo(id, tipo);
            TotalPorCategoriaResponse atual = totais.getOrDefault(chave,
                new TotalPorCategoriaResponse(id, nome, tipo, zero));
            totais.put(chave, new TotalPorCategoriaResponse(id, nome, tipo,
                atual.total().add(lancamento.getValor())));
        }
        return totais.values().stream()
            .sorted(Comparator.comparing(TotalPorCategoriaResponse::categoriaId,
                Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(total -> total.tipoLancamento().name()))
            .toList();
    }

    private record CategoriaTipo(Long categoriaId, TipoLancamento tipoLancamento) {}

    private LocalDate fechamento(YearMonth mes, int dia) {
        return mes.atDay(Math.min(dia, mes.lengthOfMonth()));
    }

    private YearMonth cicloDaData(LocalDate data, int diaFechamento) {
        YearMonth mes = YearMonth.from(data);
        return data.isAfter(fechamento(mes, diaFechamento)) ? mes.plusMonths(1) : mes;
    }

    private PlanejamentoFinanceiroResponse respostaConfiguracao(PlanejamentoFinanceiro planejamento) {
        LocalDate hoje = LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo")));
        return PlanejamentoFinanceiroMapper.toResponse(planejamento,
            cicloDaData(hoje, planejamento.getDiaFechamento()).toString(),
            cicloDaData(planejamento.getDataInicioControle(), planejamento.getDiaFechamento()).toString());
    }

    private Usuario buscarUsuario(String emailAutenticado) {
        return usuarioRepository.findByEmail(emailAutenticado)
            .orElseThrow(UsuarioAutenticadoNaoEncontradoException::new);
    }

    private PlanejamentoFinanceiro buscarPlanejamento(Usuario usuario) {
        return planejamentoRepository.findByUsuarioId(usuario.getId())
            .orElseThrow(PlanejamentoNaoEncontradoException::new);
    }
}
