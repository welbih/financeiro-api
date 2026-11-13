CREATE TABLE usuarios_roles (
    usuario_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT pk_usuarios_roles PRIMARY KEY (usuario_id, role_id),
    CONSTRAINT fk_usuarios_roles_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT fk_usuarios_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE INDEX idx_usuarios_roles_role_id ON usuarios_roles (role_id);
