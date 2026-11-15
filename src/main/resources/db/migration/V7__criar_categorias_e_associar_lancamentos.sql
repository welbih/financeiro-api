CREATE SEQUENCE categorias_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE categorias (
    id BIGINT DEFAULT nextval('categorias_seq') PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    tipo_lancamento VARCHAR(7) NOT NULL,
    usuario_id BIGINT NOT NULL,
    CONSTRAINT fk_categorias_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT ck_categorias_nome CHECK (length(trim(nome)) > 0),
    CONSTRAINT ck_categorias_tipo CHECK (tipo_lancamento IN ('DESPESA', 'RECEITA')),
    CONSTRAINT uk_categorias_id_usuario_tipo UNIQUE (id, usuario_id, tipo_lancamento)
);

ALTER SEQUENCE categorias_seq OWNED BY categorias.id;
CREATE UNIQUE INDEX uk_categorias_usuario_nome_normalizado ON categorias (usuario_id, lower(btrim(nome)));

ALTER TABLE lancamentos ADD COLUMN categoria_id BIGINT;

-- Preserva os registros existentes, com uma categoria de transicao por usuario e tipo.
INSERT INTO categorias (nome, tipo_lancamento, usuario_id)
SELECT DISTINCT
    CASE tipo_lancamento WHEN 'DESPESA' THEN 'Sem categoria (despesa)' ELSE 'Sem categoria (receita)' END,
    tipo_lancamento, usuario_id
FROM lancamentos;

UPDATE lancamentos AS lancamento
SET categoria_id = categoria.id
FROM categorias AS categoria
WHERE categoria.usuario_id = lancamento.usuario_id
  AND categoria.tipo_lancamento = lancamento.tipo_lancamento;

ALTER TABLE lancamentos ALTER COLUMN categoria_id SET NOT NULL;
ALTER TABLE lancamentos ADD CONSTRAINT fk_lancamentos_categoria_usuario_tipo
    FOREIGN KEY (categoria_id, usuario_id, tipo_lancamento)
    REFERENCES categorias (id, usuario_id, tipo_lancamento)
    ON DELETE RESTRICT ON UPDATE RESTRICT;
CREATE INDEX idx_lancamentos_categoria ON lancamentos (categoria_id);
