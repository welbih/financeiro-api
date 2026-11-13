INSERT INTO roles (id, authority)
VALUES (nextval('roles_seq'), 'ROLE_ADMIN')
ON CONFLICT (authority) DO NOTHING;

INSERT INTO usuarios (id, nome, sobrenome, email, senha)
VALUES (nextval('usuarios_seq'), 'Admin', 'Sistema', 'admin@financeiro.local', '$2a$10$SWuiIvwxmwRGRvJpuI81Z.dnxxQo.A/RLWXGXQoV4c3cxUt2yPQb.')
ON CONFLICT (email) DO NOTHING;

INSERT INTO usuarios_roles (usuario_id, role_id)
SELECT usuarios.id, roles.id
FROM usuarios
CROSS JOIN roles
WHERE usuarios.email = 'admin@financeiro.local'
  AND roles.authority = 'ROLE_ADMIN'
ON CONFLICT (usuario_id, role_id) DO NOTHING;
