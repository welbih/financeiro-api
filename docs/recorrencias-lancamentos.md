# Recorrências no cadastro

`POST /lancamentos` aceita o campo opcional `quantidadeRecorrencias`, de 0 a 12.
O número corresponde aos meses adicionais: 2 cria o original e mais dois registros.
O campo ausente ou nulo equivale a zero.

```json
{
  "tipoLancamento": "DESPESA",
  "descricao": "Internet",
  "valor": 100.00,
  "pago": false,
  "dataVencimento": "2027-01-31",
  "dataPrevisaoPagamento": "2027-01-30",
  "quantidadeRecorrencias": 2
}
```

Nesse exemplo os vencimentos são 31/01, 28/02 e 31/03. As previsões são
30/01, 28/02 e 30/03. Cada data é deslocada a partir da original, ajustando
para o último dia quando necessário. Previsão ausente continua ausente.

Todos os demais atributos são copiados, inclusive categoria, valor, `pago` e
`dataPagamento`. Para gerar parcelas pendentes, cadastre com `pago: false` e sem
`dataPagamento`. O usuário de todos os registros é o usuário autenticado.

A resposta permanece 201 com o lançamento original. Os demais aparecem na listagem
e nos resumos existentes. A criação é transacional: todos são salvos ou nenhum.
Não há vínculo de série nem geração automática futura; cada registro pode ser
editado, pago ou removido individualmente. Não é necessária migration.

`PUT /lancamentos/{id}` não gera recorrências: envie o campo ausente, nulo ou zero.
Valores positivos na atualização retornam 400.
