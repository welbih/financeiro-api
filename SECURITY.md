# Autenticacao

## Logout

Envie `POST /auth/logout`, sem corpo, com `Authorization: Bearer <access_token>`.
Nao use Basic Auth nesse endpoint. Retorna `204 No Content` e invalida somente
o token apresentado, mantendo o registro da autorizacao para consulta.
Outras sessoes do mesmo usuario continuam ativas. Um novo login gera outro token.

Requisicoes seguintes com o token revogado retornam `401`, inclusive repetir
o logout com esse token. Tokens ausentes, invalidos ou expirados tambem retornam
`401`. O corpo da requisicao nao e usado para escolher qual token revogar.
Apos o sucesso, o cliente deve remover o token de seu armazenamento.

Alem da assinatura, emissor, audiencia e expiracao, o Resource Server consulta
a autorizacao a cada validacao de token e rejeita autorizacoes inexistentes ou
inativas. A revogacao pelo endpoint OAuth2 `/oauth2/revoke` tambem e respeitada.
Em producao, essa consulta usa PostgreSQL: revogacoes sobrevivem a reinicios e
sao compartilhadas entre replicas. A migration V8 adiciona indice para a busca
por access token. Em `dev`, a autorizacao usa o armazenamento em memoria.
Essa verificacao acrescenta acesso ao armazenamento de autorizacoes nas
requisicoes autenticadas; falhas de armazenamento nao liberam acesso.

## Categorias

Todas as rotas exigem Bearer Token e usam o usuario do JWT:

| Metodo | Rota | Retorno |
| --- | --- | --- |
| POST | /categorias | 201 com a categoria criada |
| GET | /categorias?page=0&size=20 | 200 com pagina das categorias do usuario |
| GET | /categorias/{id} | 200 com uma categoria do usuario |
| PUT | /categorias/{id} | 200 com a categoria atualizada |
| DELETE | /categorias/{id} | 204 quando a categoria nao esta em uso |

JSON de cadastro/atualizacao:

```json
{
  "nome": "Alimentacao",
  "tipoLancamento": "DESPESA"
}
```

Tipo aceita `DESPESA` ou `RECEITA`. Nome e obrigatorio, limitado a 100
caracteres e salvo sem espacos nas extremidades. Nomes duplicados por usuario
retornam `409`, ignorando diferencas de maiusculas/minusculas, mesmo quando o
tipo e diferente. Usuarios diferentes podem usar o mesmo nome. A migration
tambem cria um indice unico no PostgreSQL para proteger requisicoes concorrentes.

A listagem aceita `tipoLancamento=DESPESA` ou `tipoLancamento=RECEITA`,
pagina inicial zero e tamanho de 1 a 100, ordenada por nome e ID. A resposta
tem `content`, `page`, `size`, `totalElements` e `totalPages`. O objeto da
categoria inclui `id`, `nome`, `tipoLancamento` e `usuarioId`, sem entidade Usuario.

IDs inexistentes ou de outro usuario retornam `404`. Categoria em uso nao
pode ser excluida nem ter seu tipo alterado (`409`), mas pode ser renomeada.
Excluir lancamento nunca exclui a categoria; excluir categoria nunca exclui
lancamentos. Nao ha cascata de remocao nessas associacoes.

Categoria e opcional no cadastro e na atualizacao de lancamentos: omita
`categoriaId` ou envie `null` para deixar sem categoria. No PUT, isso tambem
remove a associacao anterior, sem excluir a categoria.
Quando informado, o ID deve ser positivo e a categoria deve pertencer ao proprio
usuario e ter o mesmo tipo do lancamento.
ID invalido ou tipo incompativel retorna `400`; categoria inexistente
ou de outro usuario retorna `404`. A resposta do lancamento inclui `categoriaId`.

A migration V7 preserva lancamentos existentes: cria categorias de transicao
por usuario e tipo, chamadas `Sem categoria (despesa)` e `Sem categoria (receita)`,
somente quando ha registros anteriores, e associa esses registros. A migration
V9 torna a coluna opcional sem remover categorias ou associacoes existentes.
A chave estrangeira composta valida categoria, usuario e tipo quando ha categoria
no PostgreSQL, com remocao restrita. Novos usuarios criam suas categorias pelo CRUD.

## Resumo mensal de lancamentos

`GET /lancamentos/resumo-mensal?mes=2026-12` exige Bearer Token e retorna:
`mes`, `totalDespesas`, `totalReceitas`, `diferenca` e `lancamentos`.

O parametro `mes` e obrigatorio no formato `AAAA-MM`, incluindo o ano. O mes
e determinado por `dataPrevisaoPagamento`, quando informada; caso contrario,
usa `dataVencimento`. A data de pagamento efetivo nao altera esse agrupamento.
Inclui lancamentos pagos e pendentes do usuario autenticado, sem paginacao,
ordenados pela data usada no agrupamento e ID. `diferenca = totalReceitas - totalDespesas` e
pode ser negativa. As somas usam BigDecimal, sem conversao para ponto flutuante.
Totais e registros sao calculados a partir da mesma consulta filtrada no banco.

Sem registros, retorna `200` com totais `0.00` e `lancamentos: []`.
Mes ausente ou invalido retorna `400`; ausencia de token retorna `401`.

## Listagem, atualizacao e remocao de lancamentos

Todas as rotas exigem Bearer Token e consultam apenas registros do usuario
autenticado, inclusive quando ele possui `ROLE_ADMIN`:

| Metodo | Rota | Retorno |
| --- | --- | --- |
| GET | /lancamentos?page=0&size=20 | 200 com pagina dos proprios lancamentos |
| PUT | /lancamentos/{id} | 200 com o lancamento atualizado |
| DELETE | /lancamentos/{id} | 204 sem corpo |

A listagem tem `content`, `page`, `size`, `totalElements` e `totalPages`.
Paginas comecam em zero, `size` vai de 1 a 100 e a ordenacao e por vencimento
e ID, ambos crescentes. Sem registros, retorna pagina vazia com `200`.

O PUT usa o mesmo JSON do cadastro e substitui todos os campos editaveis;
envie os campos obrigatorios. Datas opcionais omitidas sao removidas e
`pago` omitido assume `false`. E possivel corrigir a data de um registro pago
ou reabri-lo enviando `pago=false` e `dataPagamento=null`. O proprietario
permanece o mesmo; usuario ou usuarioId enviados sao ignorados.

Atualizacao e remocao retornam `404` tanto para IDs inexistentes quanto para
registros de outro usuario. IDs invalidos, paginacao invalida ou dados de
atualizacao inconsistentes retornam `400`. Sem token, retornam `401`.

## Cadastro de lancamentos

`POST /lancamentos` exige Bearer Token valido e usuario cadastrado no banco.
O proprietario e obtido pelo email do claim `sub` do JWT. Nao envie usuario
no JSON: `usuario` e `usuarioId`, se presentes, sao ignorados.

```json
{
  "tipoLancamento": "DESPESA",
  "descricao": "Conta de energia",
  "valor": 150.25,
  "pago": false,
  "dataVencimento": "2026-12-10",
  "dataPrevisaoPagamento": "2026-12-09",
  "dataPagamento": null,
  "categoriaId": 1
}
```

O retorno `201` inclui o ID do lancamento e `usuarioId`, sem expor a entidade
Usuario. O tipo aceita `DESPESA` ou `RECEITA`, o valor deve ser positivo, com
ate duas casas decimais, a descricao e limitada a 500 caracteres e o
vencimento e obrigatorio. Datas usam `AAAA-MM-DD`.
Se `pago` for omitido ou nulo, assume `false`. Quando `pago=true`, informe
`dataPagamento`; quando `false`, deixe essa data nula ou omitida. A previsao
e opcional. Entrada inconsistente retorna `400`; ausencia de token ou usuario
do token inexistente no banco retorna `401`. Nao e exigida `ROLE_ADMIN`.

## Diagnostico de invalid_client

Falhas OAuth retornam o codigo de erro publico e um identificador no header
`X-Auth-Error-Id`. Pesquise o mesmo identificador nos logs do servico da API
no Railway: `OAuth2 authentication rejected: reference=... reason=...`.
Os logs registram apenas categorias fixas, sem usuario, senha, token,
cabecalho Authorization ou descricao livre da excecao.

- `CLIENT_ID_NAO_ENCONTRADO`: o identificador recebido nao corresponde ao cliente registrado.
- `CLIENT_SECRET_NAO_CONFERE`: o cliente existe, mas o segredo recebido nao confere.
- `BASIC_AUTH_AUSENTE_OU_INCORRETO`: nao chegou um cabecalho com o esquema Basic.
- `METODO_CLIENTE_NAO_PERMITIDO`: o metodo de autenticacao do cliente nao esta habilitado.
- `REQUISICAO_OU_BASIC_AUTH_MALFORMADO`: formato de credenciais ou parametros invalido.
- `AUTHORIZATION_DUPLICADO`: chegaram varios cabecalhos Authorization.

O PasswordEncoder usado para conferir o segredo do cliente e configurado
explicitamente com o mesmo BCrypt usado ao registrar o cliente.

## Cadastro de usuarios

### Alteracao de senha

`PUT /usuarios/senha` exige Bearer token e altera apenas a senha do usuario
identificado pelo token. Corpo:

```json
{"senhaAtual":"SenhaAtual123!","novaSenha":"NovaSenhaForte123!"}
```

A senha atual e conferida com BCrypt antes da gravacao. A nova senha deve ter
ao menos 8 caracteres, incluindo maiuscula, minuscula, numero e caractere especial (espaco nao conta), e no maximo 72 bytes UTF-8, como no cadastro.
Retorna `204` sem corpo; senha atual incorreta ou dados invalidos retornam `400`.
Sem token valido retorna `401`. As senhas nao sao devolvidas na resposta.
Tokens ja emitidos permanecem validos ate expirarem ou serem revogados no logout.

### Cadastro publico

`POST /usuarios` esta temporariamente publico, sem exigir token ou role.
Envie JSON com nome, sobrenome, email e senha original:

```json
{
  "nome": "Maria",
  "sobrenome": "Silva",
  "email": "maria@example.com",
  "senha": "UmaSenhaForte123!"
}
```

A API atribui exclusivamente `ROLE_USER`. Enviar `roles` retorna `400`.
A role precisa existir no banco; o endpoint nao cria permissoes. Em producao,
provisione diretamente no PostgreSQL antes de liberar cadastros:

```sql
INSERT INTO roles (authority) VALUES ('ROLE_USER')
ON CONFLICT (authority) DO NOTHING;
```

O seed dev inclui essa role. Se ausente, o cadastro falha sem criar usuario
(erro de configuracao do servidor).
A senha deve ter ao menos 8 caracteres, incluindo maiuscula, minuscula, numero e caractere especial (espaco nao conta), e no maximo 72 bytes UTF-8, e e salva
como hash BCrypt. O retorno `201` inclui id, nome, sobrenome, email e roles,
sem senha ou hash. Email duplicado retorna `409`; entrada invalida retorna `400`.
O cadastro publico nao permite conceder `ROLE_ADMIN`.
Use No Auth no Postman; um Bearer Token invalido enviado pode retornar `401`.

A API atua como Authorization Server e Resource Server. O login direto usa
um grant personalizado, integrado ao endpoint OAuth2 do Spring Security.

## Postman

Envie `POST http://localhost:8080/oauth2/token`.

Em Authorization, selecione Basic Auth e informe `CLIENT_ID` e `CLIENT_SECRET`.
Para uso direto com Basic Auth no Postman, prefira segredos Base64URL (sem `+`,
`/` e `=`). O Spring decodifica as credenciais segundo o formato OAuth2:
um `+` literal enviado sem codificacao form e interpretado como espaco,
resultando em `invalid_client`. Para manter um segredo com caracteres especiais,
codifique cada credencial como application/x-www-form-urlencoded antes de
montar o Base64 de `client_id:client_secret`. Nao altere o valor original no
Railway para a versao codificada.
Em Body, selecione `x-www-form-urlencoded`:

| Campo | Valor |
| --- | --- |
| grant_type | urn:financeiro:params:oauth:grant-type:password |
| username | Email do usuario |
| password | Senha do usuario |
| scope | api.read api.write (opcional) |

O usuario do seed dev usa `admin@financeiro.local`. A senha correspondente ao
hash atual do seed e `123456`.

O seed dev tambem inclui `usuario@financeiro.local`, ativo, com `ROLE_USER` e
senha `Usuario123!`, armazenada como hash BCrypt.

A resposta inclui `access_token`, `token_type`, `expires_in` e `scope`.
Use `Authorization: Bearer <access_token>` nas requisicoes aos recursos.
O JWT inclui o email como `sub`, as roles no claim `authorities`, o emissor,
a audiencia e a expiracao. A duracao e definida por `JWT_DURATION`, em segundos.

Credenciais de usuario incorretas retornam `invalid_grant`; cliente incorreto
retorna `invalid_client`. A API exige JWT nos endpoints de recursos. Regras
por role podem usar `@PreAuthorize("hasRole('ADMIN')")`.

## Configuracao

As propriedades compartilhadas ficam em `application.properties`:
`CLIENT_ID`, `CLIENT_SECRET`, `JWT_DURATION`, `JWT_ISSUER` e `CORS_ORIGINS`.
`JWT_ISSUER` deve corresponder a URL publica do servidor; em producao use HTTPS.
`CORS_ORIGINS` recebe origens separadas por virgula.

No profile `dev`, a chave RSA e gerada na inicializacao; tokens anteriores
deixam de ser validos ao reiniciar.

Fora de `dev`, configure tambem `JWT_JWK` com uma chave RSA privada persistente
em JSON JWK, de pelo menos 2048 bits, contendo `kid`. A chave nao e versionada.
O Resource Server verifica assinatura, emissor, audiencia, validade temporal
e se a autorizacao do token permanece ativa.

O cliente e definido pelas variaveis de ambiente. Em `dev`, os registros de
autorizacao ficam em memoria; fora de `dev`, sao persistidos via JDBC na tabela
`oauth2_authorization`. Nao ha refresh token neste fluxo. Autorizacoes revogadas
pelo logout ou pelo Authorization Server sao rejeitadas na proxima validacao
do token pelo Resource Server.

O profile padrao e `prod`, com migrations e sem seed. Apenas `dev` carrega
`.env` e recria as tabelas e seeds. Consulte `DEPLOY.md` para publicar no Railway.
