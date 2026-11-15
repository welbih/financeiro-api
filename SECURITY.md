# Autenticacao

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

`POST /usuarios` esta temporariamente publico, sem exigir token ou role.
Envie JSON com nome, sobrenome, email, senha original e nomes das permissoes:

```json
{
  "nome": "Maria",
  "sobrenome": "Silva",
  "email": "maria@example.com",
  "senha": "UmaSenhaForte123!",
  "roles": ["ROLE_ADMIN"]
}
```

As roles precisam existir no banco; o endpoint nao cria novas permissoes.
A senha deve ter ao menos 12 caracteres e no maximo 72 bytes UTF-8, e e salva
como hash BCrypt. O retorno `201` inclui id, nome, sobrenome, email e roles,
sem senha ou hash. Email duplicado retorna `409`; entrada invalida ou role
inexistente retorna `400`. Enquanto a rota estiver publica, qualquer pessoa
pode cadastrar usuarios com as roles existentes, inclusive `ROLE_ADMIN`.
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
O Resource Server verifica assinatura, emissor, audiencia e validade temporal.

O cliente e definido pelas variaveis de ambiente. Em `dev`, os registros de
autorizacao ficam em memoria; fora de `dev`, sao persistidos via JDBC na tabela
`oauth2_authorization`. Nao ha refresh token neste fluxo. JWTs ja emitidos
continuam validos ate expirarem, mesmo apos revogacao no Authorization Server;
revogacao imediata no Resource Server exige configuracao adicional.

O profile padrao e `prod`, com migrations e sem seed. Apenas `dev` carrega
`.env` e recria as tabelas e seeds. Consulte `DEPLOY.md` para publicar no Railway.
