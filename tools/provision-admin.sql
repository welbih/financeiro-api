-- Executar manualmente com psql, depois das migrations; nao e carregado pela API.
-- Variaveis psql obrigatorias: admin_nome, admin_sobrenome, admin_email, admin_senha_hash.
\set ON_ERROR_STOP on
BEGIN;

INSERT INTO roles (id, authority)
VALUES (nextval('roles_seq'), 'ROLE_ADMIN')
ON CONFLICT (authority) DO NOTHING;

INSERT INTO usuarios (id, nome, sobrenome, email, senha)
VALUES (nextval('usuarios_seq'), :'admin_nome', :'admin_sobrenome', :'admin_email', :'admin_senha_hash')
ON CONFLICT (email) DO NOTHING;

INSERT INTO usuarios_roles (usuario_id, role_id)
SELECT usuarios.id, roles.id
FROM usuarios CROSS JOIN roles
WHERE usuarios.email = :'admin_email' AND roles.authority = 'ROLE_ADMIN'
ON CONFLICT (usuario_id, role_id) DO NOTHING;

COMMIT;
