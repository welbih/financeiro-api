# Autenticacao

A API atua como Authorization Server e Resource Server. O login direto usa
um grant personalizado, integrado ao endpoint OAuth2 do Spring Security.

## Postman

Envie `POST http://localhost:8080/oauth2/token`.

Em Authorization, selecione Basic Auth e informe `CLIENT_ID` e `CLIENT_SECRET`.
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

O cliente registrado e os registros de autorizacao ficam em memoria. Nao ha
refresh token neste fluxo. A reinicializacao perde os registros de autorizacao;
com uma chave persistente, JWTs ja emitidos continuam validos ate expirarem.
Persistencia e revogacao de tokens exigem configuracao adicional.

As migracoes e configuracoes de banco de producao devem ser habilitadas no
ambiente correspondente; o profile `dev` continua recriando as tabelas e seeds.
