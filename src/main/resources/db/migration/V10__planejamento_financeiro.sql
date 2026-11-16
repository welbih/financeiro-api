CREATE SEQUENCE planejamentos_financeiros_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE planejamentos_financeiros (
    id BIGINT PRIMARY KEY DEFAULT nextval('planejamentos_financeiros_seq'),
    usuario_id BIGINT NOT NULL UNIQUE REFERENCES usuarios(id),
    dia_fechamento INTEGER NOT NULL CHECK (dia_fechamento BETWEEN 1 AND 31),
    data_inicio_controle DATE NOT NULL,
    saldo_inicial NUMERIC(19, 2) NOT NULL
);
