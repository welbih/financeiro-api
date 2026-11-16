CREATE SEQUENCE lancamentos_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE lancamentos (
    id BIGINT DEFAULT nextval('lancamentos_seq') PRIMARY KEY,
    tipo_lancamento VARCHAR(7) NOT NULL,
    usuario_id BIGINT NOT NULL,
    descricao VARCHAR(500) NOT NULL,
    valor NUMERIC(19, 2) NOT NULL,
    pago BOOLEAN NOT NULL DEFAULT FALSE,
    data_vencimento DATE NOT NULL,
    data_previsao_pagamento DATE,
    data_pagamento DATE,
    CONSTRAINT fk_lancamentos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT ck_lancamentos_tipo CHECK (tipo_lancamento IN ('DESPESA', 'RECEITA')),
    CONSTRAINT ck_lancamentos_valor CHECK (valor > 0),
    CONSTRAINT ck_lancamentos_descricao CHECK (length(trim(descricao)) > 0),
    CONSTRAINT ck_lancamentos_pagamento CHECK (pago = (data_pagamento IS NOT NULL))
);

ALTER SEQUENCE lancamentos_seq OWNED BY lancamentos.id;
CREATE INDEX idx_lancamentos_usuario_vencimento ON lancamentos (usuario_id, data_vencimento);
