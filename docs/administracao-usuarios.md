# Administração de usuários

As rotas exigem Bearer token com `ROLE_ADMIN`. Sem autenticação retornam 401;
usuários sem essa role recebem 403.

## Listar

`GET /usuarios?page=0&size=20`

Retorna 200 com `content`, `page`, `size`, `totalElements` e `totalPages`.
Cada usuário contém `id`, `nome`, `sobrenome`, `email`, `roles` e `ativo`.
Inclui ativos e inativos, ordenados por nome e id. Página mínima 0; tamanho de
1 a 100, padrão 20. Senhas e hashes não são expostos.

## Inativar

`PATCH /usuarios/{id}/inativar`, sem body.

Retorna 204, inclusive se já estiver inativo. Id inexistente retorna 404.
A operação preserva o usuário e seus dados financeiros.
Administradores podem inativar qualquer usuário, inclusive a própria conta.

Usuários inativos não conseguem fazer login (`invalid_grant`) e seus tokens
já emitidos deixam de ser aceitos pela API (401). Cada validação de token consulta
o estado atual do usuário. A migration V11 adiciona `ativo`, mantendo os usuários
existentes ativos. Novos cadastros também são ativos.

## Ativar

`PATCH /usuarios/{id}/ativar`, sem body, exclusivo para `ROLE_ADMIN`.

Retorna 204, inclusive se já estiver ativo. Id inexistente retorna 404.
O usuário volta a conseguir fazer login. Tokens anteriores não revogados e ainda
não expirados também voltam a ser aceitos, pois a inativação bloqueia o acesso
pelo estado atual do usuário, sem revogar as autorizações.
