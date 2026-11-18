INSERT INTO roles (id, authority)
VALUES (nextval('roles_seq'), 'ROLE_ADMIN')
ON CONFLICT (authority) DO NOTHING;

INSERT INTO roles (id, authority)
VALUES (nextval('roles_seq'), 'ROLE_USER')
ON CONFLICT (authority) DO NOTHING;

INSERT INTO usuarios (id, nome, sobrenome, email, senha, ativo)
VALUES (nextval('usuarios_seq'), 'Admin', 'Sistema', 'admin@financeiro.local', '$2a$10$SWuiIvwxmwRGRvJpuI81Z.dnxxQo.A/RLWXGXQoV4c3cxUt2yPQb.', true)
ON CONFLICT (email) DO NOTHING;

INSERT INTO usuarios_roles (usuario_id, role_id)
SELECT usuarios.id, roles.id
FROM usuarios
CROSS JOIN roles
WHERE usuarios.email = 'admin@financeiro.local'
  AND roles.authority = 'ROLE_ADMIN'
ON CONFLICT (usuario_id, role_id) DO NOTHING;

-- Credenciais locais: usuario@financeiro.local / Usuario123!
INSERT INTO usuarios (id, nome, sobrenome, email, senha, ativo)
VALUES (nextval('usuarios_seq'), 'Usuario', 'Sistema', 'usuario@financeiro.local', '$2a$10$LvrsABiSASCgnKZqBsYLVe1pUEu.oxzekcL1IHYf4Q/tDOc1WpbIO', true)
ON CONFLICT (email) DO NOTHING;

INSERT INTO usuarios_roles (usuario_id, role_id)
SELECT usuarios.id, roles.id
FROM usuarios
CROSS JOIN roles
WHERE usuarios.email = 'usuario@financeiro.local'
  AND roles.authority = 'ROLE_USER'
ON CONFLICT (usuario_id, role_id) DO NOTHING;
