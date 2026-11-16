# Pagar lançamento

`PATCH /lancamentos/{id}/pagar` exige Bearer token e altera somente o lançamento
do usuário autenticado. Funciona para despesas e receitas (recebimento).

Corpo opcional:

```json
{"data":"2026-11-16"}
```

Sem corpo, com `{}` ou com `{"data":null}`, registra a data atual em
`America/Sao_Paulo`. Datas informadas devem usar o formato `yyyy-MM-dd`.

Retorna 200 com o lançamento atualizado, `pago: true` e `dataPagamento` preenchida.
Os demais atributos são preservados. Retorna 400 para data inválida ou lançamento
já pago; 404 se inexistente ou de outro usuário; 401 sem autenticação válida.
