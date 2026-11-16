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

## Consultar um ciclo

`GET /planejamento-financeiro/resumo?mesFechamento=2026-12`

O parâmetro identifica o mês do fechamento. No exemplo, o período inclusivo é
10/11/2026 a 09/12/2026. Ciclos anteriores ao início do controle retornam 400.

A resposta contém `mesFechamento`, `dataInicial`, `dataFinal`, `saldoInicialProjetado`,
`totalReceitas`, `totalDespesas`, `receitasRecebidas`, `receitasPendentes`,
`despesasPagas`, `despesasPendentes`, `resultadoCiclo`, `saldoFinalProjetado`
e todos os `lancamentos` do período, ordenados por data e id.

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
