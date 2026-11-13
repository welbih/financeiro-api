ALTER TABLE usuarios ALTER COLUMN id DROP IDENTITY IF EXISTS;

CREATE SEQUENCE usuarios_seq START WITH 1 INCREMENT BY 1;

SELECT setval('usuarios_seq', COALESCE((SELECT MAX(id) FROM usuarios), 0) + 1, false);

ALTER SEQUENCE usuarios_seq OWNED BY usuarios.id;

ALTER TABLE usuarios ALTER COLUMN id SET DEFAULT nextval('usuarios_seq');
