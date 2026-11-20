# Planejamento financeiro

Uma configuração por usuário autenticado; não são criados registros mensais.
Todos os endpoints exigem Bearer token. O usuário é identificado pelo token.

## Configurar

`PUT /planejamento-financeiro` cria ou atualiza a configuração (200):

```json
{
  "diaFechamento": 9,
  "dataInicioControle": "2026-11-10",
  "saldoInicial": 300.00
}
```

`saldoInicial` é o saldo disponível antes dos movimentos da data inicial e pode ser negativo.
O início deve ser o dia seguinte a um fechamento. O dia de fechamento aceita 1 a 31;
em meses menores, usa o último dia do mês. Por exemplo, fechamento 31 permite início
em 1 de março, após o fechamento de fevereiro.

`GET /planejamento-financeiro` retorna a configuração (200), ou 404 se ainda não existir.

## Ciclo atual e início da navegação

As respostas de `GET /planejamento-financeiro` e `PUT /planejamento-financeiro`
incluem os campos adicionais `cicloAtual` e `primeiroCicloDisponivel`, ambos strings
no formato `YYYY-MM`. Os campos anteriores, parâmetros e códigos HTTP são preservados.
Não é necessário alterar o corpo do PUT.

Exemplo de resposta em **20/11/2026**, considerando `America/Sao_Paulo`:

```json
{
  "id": 1,
  "diaFechamento": 9,
  "dataInicioControle": "2026-11-10",
  "saldoInicial": 300.00,
  "usuarioId": 10,
  "cicloAtual": "2026-12",
  "primeiroCicloDisponivel": "2026-12"
}
```

- `cicloAtual`: mês de fechamento do ciclo que contém a data de hoje em São Paulo.
  O dia do fechamento ainda pertence ao ciclo que termina naquele dia; o ciclo muda
  à meia-noite local do dia seguinte. Com fechamento 9, em 09/11 o ciclo é `2026-11`;
  em 10/11 é `2026-12`; em 10/12 é `2027-01`.
- `primeiroCicloDisponivel`: mês de fechamento do ciclo iniciado em `dataInicioControle`.
  É o menor valor aceito pelo resumo. Não depende de existirem lançamentos nem de
  consultas anteriores. Muda se a configuração do planejamento for alterada.

As duas identificações reutilizam a mesma regra de fechamento do resumo e da
validação de início do controle. Se o dia configurado não existir no mês, considera-se
o último dia: fechamento 31 em fevereiro usa 28 ou 29. Com esse fechamento e início
em 01/03, o primeiro ciclo é março, inclusive em anos bissextos.

### Uso no frontend

1. Consulte `GET /planejamento-financeiro` ao abrir o planejamento. Use `cicloAtual`
   para selecionar o ciclo inicial e `primeiroCicloDisponivel` como limite inferior
   da navegação. Se o controle começar no futuro, selecione o maior dos dois valores:
   o ciclo atual representa hoje e não é artificialmente deslocado para o futuro.
2. Envie o ciclo selecionado diretamente para
   `/planejamento-financeiro/resumo?mesFechamento=YYYY-MM`.
3. Desabilite a navegação para ciclos anteriores a `primeiroCicloDisponivel`.
   Não derive esses campos de `diaFechamento`, datas do navegador ou lançamentos.
   É possível comparar as strings no formato retornado para determinar o limite.
4. Após editar a configuração, utilize os campos retornados pelo PUT e revalide a
   seleção. Reconsulte o GET ao retomar a tela ou atravessar a meia-noite de São Paulo,
   pois `cicloAtual` é calculado na consulta, sem persistência mensal.

O backend continua rejeitando resumos anteriores ao controle com 400. Sem configuração,
o GET continua retornando 404. Não há limite superior novo para navegar em projeções.
Os cálculos de saldos, receitas, despesas e as regras de seleção de lançamentos não mudam.

## Consultar um ciclo

`GET /planejamento-financeiro/resumo?mesFechamento=2026-12`

O parâmetro identifica o mês do fechamento. No exemplo, o período inclusivo é
10/11/2026 a 09/12/2026. Ciclos anteriores ao início do controle retornam 400.

A resposta contém `mesFechamento`, `dataInicial`, `dataFinal`, `saldoInicialProjetado`,
`totalReceitas`, `totalDespesas`, `receitasRecebidas`, `receitasPendentes`,
`despesasPagas`, `despesasPendentes`, `resultadoCiclo`, `saldoFinalProjetado`
e todos os `lancamentos` do período, ordenados por data e id, além de `totaisPorCategoria`.

### Totais por categoria

O resumo inclui uma coleção adicional `totaisPorCategoria`, calculada sobre todos
os lançamentos retornados em `lancamentos`, sem paginação. Usa exatamente o mesmo
usuário, ciclo inclusivo e data efetiva (previsão de pagamento, ou vencimento se
ausente). Lançamentos pagos e pendentes participam, assim como nos totais do ciclo.
O saldo inicial e os movimentos de ciclos anteriores não participam do agrupamento.

Cada item contém `categoriaId`, `categoriaNome`, `tipoLancamento` (`RECEITA` ou
`DESPESA`) e `total`, a soma dos valores daquele grupo. Esses campos substituem
os antigos `totalReceitas` e `totalDespesas` apenas dentro dos itens da coleção;
os totais gerais e demais campos do resumo permanecem inalterados.
Os valores monetários são somados com `BigDecimal`, sem conversão para ponto flutuante.
O agrupamento usa o id da categoria e o tipo do lançamento, não o nome, e mantém
receitas e despesas em itens separados, mesmo quando a categoria é a mesma. A validação existente de
compatibilidade entre o tipo da categoria e o lançamento permanece inalterada.

Lançamentos sem categoria formam um item por tipo, com `categoriaId: null` e
`categoriaNome: "Sem categoria"`. Se houver receitas e despesas sem categoria,
serão dois itens; se houver apenas um tipo, apenas seu grupo será retornado.
Apenas categorias com lançamentos no ciclo aparecem. A ordenação é por
`categoriaId` crescente, com os grupos sem categoria ao final e, para um mesmo id,
por `tipoLancamento` em ordem alfabética (`DESPESA`, `RECEITA`). O nome é o nome atual
da categoria. Um ciclo vazio retorna `totaisPorCategoria: []`.

Exemplo de trecho da resposta de
`GET /planejamento-financeiro/resumo?mesFechamento=2026-12`
(demais campos e a lista de lançamentos omitidos neste exemplo):

```json
{
  "mesFechamento": "2026-12",
  "dataInicial": "2026-11-10",
  "dataFinal": "2026-12-09",
  "saldoInicialProjetado": 300.00,
  "totalReceitas": 4000.00,
  "totalDespesas": 3500.00,
  "resultadoCiclo": 500.00,
  "saldoFinalProjetado": 800.00,
  "totaisPorCategoria": [
    {
      "categoriaId": 10,
      "categoriaNome": "Salário",
      "tipoLancamento": "RECEITA",
      "total": 3900.00
    },
    {
      "categoriaId": 20,
      "categoriaNome": "Moradia",
      "tipoLancamento": "DESPESA",
      "total": 3000.00
    },
    {
      "categoriaId": null,
      "categoriaNome": "Sem categoria",
      "tipoLancamento": "DESPESA",
      "total": 500.00
    },
    {
      "categoriaId": null,
      "categoriaNome": "Sem categoria",
      "tipoLancamento": "RECEITA",
      "total": 100.00
    }
  ]
}
```

A soma de `total` dos itens de tipo `RECEITA` é igual ao `totalReceitas` do ciclo;
a soma dos de tipo `DESPESA` é igual ao `totalDespesas`. O frontend pode consumir a coleção diretamente, inclusive
para gráficos, sem reagrupar uma página de lançamentos. Os contratos e parâmetros
do restante do resumo são mantidos; não é necessária migration.

A data considerada é a previsão de pagamento; quando ausente, o vencimento.
O resultado do ciclo é receitas menos despesas. O saldo inicial projetado soma ao
saldo configurado o resultado de todos os lançamentos desde o início do controle
até o dia anterior ao ciclo. O saldo final projetado soma esse saldo ao resultado do ciclo.
Lançamentos de outros usuários e anteriores ao controle não entram no cálculo.

Exemplo: saldo configurado 300, receitas de dezembro 4.000 e despesas 3.500 resultam
em saldo final 800. Janeiro começa com 800; com receitas 4.000 e despesas 4.200,
termina com 600. Consultar janeiro diretamente produz o mesmo resultado.

Esta etapa calcula projeções com lançamentos pagos e pendentes. Não representa
saldo bancário confirmado, não persiste fechamentos e não gera recorrências.
Alterar lançamentos passados ou a configuração recalcula os saldos futuros.
O resumo mensal existente em `/lancamentos/resumo-mensal` continua usando meses civis.

Em produção, o Flyway cria a tabela pela migration V10. Em dev, o Hibernate cria
a estrutura; cada usuário configura seu planejamento pela API, sem configuração automática.
