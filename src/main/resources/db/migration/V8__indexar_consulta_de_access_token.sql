-- A validacao de JWT consulta a autorizacao para reconhecer revogacoes imediatamente.
-- Hash suporta igualdade com tokens longos sem o limite de tamanho de chave do B-tree.
CREATE INDEX idx_oauth2_authorization_access_token
    ON oauth2_authorization USING hash (access_token_value);
