# Deploy no Railway com Docker

## Comportamento dos ambientes

`prod` e o profile padrao e o profile da imagem Docker. Ele usa Flyway para
migrar o banco e Hibernate apenas para validar, sem apagar tabelas ou carregar
o seed dev. As autorizacoes OAuth sao persistidas no PostgreSQL. O cliente OAuth
e definido por variaveis, com identificador estavel entre reinicios.

`dev` precisa ser ativado explicitamente, carrega `.env`, recria tabelas e
executa o seed local. Nunca ative `dev` no Railway. Os dois profiles juntos
sao rejeitados. O `.env` e as chaves privadas nao entram na imagem.

## Variaveis do servico da API

Crie um servico PostgreSQL no mesmo projeto e ambiente. Substitua `Postgres`
pelo nome real do servico nas referencias abaixo:

| Variavel | Valor |
| --- | --- |
| SPRING_PROFILES_ACTIVE | prod |
| DB_URL | jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}} |
| DB_USERNAME | ${{Postgres.PGUSER}} |
| DB_PASSWORD | ${{Postgres.PGPASSWORD}} |
| CLIENT_ID | Identificador estavel escolhido para o cliente |
| CLIENT_SECRET | Segredo aleatorio de pelo menos 32 caracteres |
| JWT_DURATION | 900 (segundos; ajuste conforme a sessao desejada) |
| JWT_ISSUER | https://dominio-publico-da-api, sem barra final |
| JWT_JWK | JSON completo de uma chave RSA privada persistente com kid |
| CORS_ORIGINS | https://dominio-do-frontend, sem barra final; varias origens separadas por virgula |
| DB_POOL_SIZE | Opcional; padrao 5 por instancia |

O Railway fornece `PORT`; a API escuta nessa porta. A URL `DATABASE_URL`
fornecida pelo PostgreSQL nao e uma URL JDBC: use a composicao acima.
Use as referencias privadas para a comunicacao entre API e banco.

Gere a chave uma vez, em uma maquina confiavel com JDK 25:

```shell
java tools/GenerateJwtJwk.java
```

A saida inteira e uma chave privada: copie para `JWT_JWK` nas variaveis do
Railway e guarde-a em um gerenciador de segredos. Nao gere uma chave diferente
a cada deploy. Replicas precisam compartilhar a mesma chave, issuer e cliente.

## Publicacao

1. Execute `./mvnw verify` (Windows: `.\mvnw.cmd verify`) com Docker disponivel.
   A suite cria um PostgreSQL isolado, aplica as cinco migrations e testa login,
   persistencia OAuth, validacao JPA e readiness sem seed de desenvolvimento.
2. Conecte o repositorio ao Railway e selecione a raiz que contem o Dockerfile.
3. Configure as variaveis acima e gere/configure o dominio publico da API.
4. Publique. `railway.toml` seleciona Dockerfile e verifica
   `/actuator/health/readiness`; esse endpoint publica somente o status,
   incluindo a disponibilidade do banco, sem detalhes internos.
5. Confirme as migrations nos logs e o health com HTTP 200 antes de usar a API.

O Dockerfile executa testes unitarios e empacota o jar em um build separado.
Testes de integracao ficam fora do build Docker por precisarem de um daemon
Docker; rode `verify` no CI antes de aprovar um deploy. O runtime usa usuario
sem privilegios e recebe SIGTERM diretamente no processo Java.

Para validar a imagem localmente:

```shell
docker build -t financeiro-api:prod .
docker run --rm -p 8080:8080 --env-file .env.prod financeiro-api:prod
```

Crie `.env.prod` localmente com os valores de producao/teste apropriados; ele e
ignorado pelo Git. Em Docker Desktop, `localhost` dentro do container nao e o
host: para um banco no host, use `host.docker.internal`. No Railway, use o host
privado do servico PostgreSQL. Nao envie `.env.prod` no build.

## Primeiro administrador

Nao existe usuario padrao em producao. Apos aplicar as migrations, execute
`tools/provision-admin.sql` manualmente em uma conexao psql segura, definindo
`admin_nome`, `admin_sobrenome`, `admin_email` e `admin_senha_hash` por `\set`.
Use somente um hash BCrypt com custo adequado (por exemplo 12), gerado fora
dos logs e usando uma senha forte. A API usa BCrypt sem prefixo `{bcrypt}`.
O script cria a role se necessario e associa o usuario sem duplicar registros.
Nao use a senha ou o usuario de demonstracao do seed dev.

Para gerar o hash em um terminal PowerShell interativo, sem exibir a senha:

```powershell
.\mvnw.cmd dependency:build-classpath "-Dmdep.outputFile=target/runtime-classpath.txt" "-Dmdep.includeScope=runtime"
java --class-path (Get-Content target/runtime-classpath.txt -Raw).Trim() tools/HashPassword.java
```

No psql, configure as variaveis e carregue o script:

```sql
\set admin_nome 'Seu nome'
\set admin_sobrenome 'Seu sobrenome'
\set admin_email 'seu-email@empresa.com'
\set admin_senha_hash 'HASH_BCRYPT_GERADO_NO_PASSO_ANTERIOR'
\i tools/provision-admin.sql
```

## Pendencias operacionais e de produto

- Configurar dominio, segredos, banco e executar o provisionamento inicial.
- Habilitar backups/PITR conforme o plano do Railway e testar restauracao.
- Configurar alertas de indisponibilidade, erros e uso de recursos. O health
  de deploy nao substitui monitoramento continuo.
- Aplicar limitacao de tentativas no endpoint de login em uma camada
  compartilhada (gateway/WAF/Redis), antes de exposicao publica.
- Configurar CI para executar `verify` antes de liberar deploys.
- Definir politicas de recuperacao de senha, bloqueio de contas, auditoria,
  revogacao de JWT e rotacao de chaves; ainda nao estao implementadas.
- Implementar os casos de uso financeiros, endpoints e autorizacao por recurso:
  atualmente ha entidades de usuario/role e infraestrutura de autenticacao.

JWTs sao validados localmente. Persistir uma autorizacao ou revoga-la no servidor
OAuth nao invalida imediatamente um JWT ja emitido no Resource Server. Use
tokens curtos enquanto nao houver politica explicita de revogacao. Nao existe
refresh token neste fluxo. O grant personalizado e para clientes confiaveis;
um client-secret nao pode ser protegido em codigo de frontend publico.

Migrations versionadas ja aplicadas nao devem ser editadas. Valide alteracoes
futuras em staging, prefira migrations compativeis com a versao anterior e
faca backup antes de alteracoes destrutivas. O Flyway roda na inicializacao.

Referencias: [Dockerfile no Railway](https://docs.railway.com/builds/dockerfiles),
[healthchecks](https://docs.railway.com/deployments/healthchecks),
[PostgreSQL](https://docs.railway.com/databases/postgresql).
