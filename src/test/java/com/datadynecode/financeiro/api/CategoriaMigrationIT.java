package com.datadynecode.financeiro.api;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class CategoriaMigrationIT {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Test
    void preservaLegadoEProtegeUnicidadeProprietarioTipoERemocoes() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .target("6").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        Long usuario = jdbc.queryForObject("INSERT INTO usuarios(nome,sobrenome,email,senha) VALUES ('Um','Teste','um@test.local','fixture') RETURNING id", Long.class);
        Long outroUsuario = jdbc.queryForObject("INSERT INTO usuarios(nome,sobrenome,email,senha) VALUES ('Dois','Teste','dois@test.local','fixture') RETURNING id", Long.class);
        Long semLancamentos = jdbc.queryForObject("INSERT INTO usuarios(nome,sobrenome,email,senha) VALUES ('Tres','Teste','tres@test.local','fixture') RETURNING id", Long.class);
        jdbc.update("INSERT INTO lancamentos(tipo_lancamento,usuario_id,descricao,valor,data_vencimento) VALUES ('DESPESA',?,'Antigo',15,'2026-01-01'),('RECEITA',?,'Antigo',25,'2026-01-01'),('DESPESA',?,'Outro',35,'2026-01-01')", usuario, usuario, outroUsuario);

        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load().migrate();
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM lancamentos WHERE categoria_id IS NOT NULL", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM categorias", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM categorias WHERE usuario_id = ?", Integer.class, semLancamentos));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM lancamentos l JOIN categorias c ON l.categoria_id=c.id WHERE l.usuario_id<>c.usuario_id OR l.tipo_lancamento<>c.tipo_lancamento", Integer.class));
        Long categoria = jdbc.queryForObject("SELECT id FROM categorias WHERE usuario_id=? AND tipo_lancamento='DESPESA'", Long.class, usuario);
        Long categoriaAlheia = jdbc.queryForObject("SELECT id FROM categorias WHERE usuario_id=? AND tipo_lancamento='DESPESA'", Long.class, outroUsuario);
        Long categoriaReceita = jdbc.queryForObject("SELECT id FROM categorias WHERE usuario_id=? AND tipo_lancamento='RECEITA'", Long.class, usuario);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO categorias(nome,tipo_lancamento,usuario_id) VALUES ('  SEM CATEGORIA (DESPESA)  ','RECEITA',?)", usuario));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM categorias WHERE id=?", categoria));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE categorias SET tipo_lancamento='RECEITA' WHERE id=?", categoria));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE lancamentos SET categoria_id=? WHERE usuario_id=? AND tipo_lancamento='DESPESA'", categoriaAlheia, usuario));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE lancamentos SET categoria_id=? WHERE usuario_id=? AND tipo_lancamento='DESPESA'", categoriaReceita, usuario));
        jdbc.update("UPDATE lancamentos SET categoria_id=NULL WHERE usuario_id=?", usuario);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM lancamentos WHERE usuario_id=? AND categoria_id IS NULL", Integer.class, usuario));
        jdbc.update("DELETE FROM lancamentos WHERE usuario_id=?", usuario);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM categorias WHERE usuario_id=?", Integer.class, usuario));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lancamentos WHERE usuario_id=?", Integer.class, outroUsuario));
    }
}
