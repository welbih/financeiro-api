CREATE SEQUENCE roles_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE roles (
    id BIGINT DEFAULT nextval('roles_seq') PRIMARY KEY,
    authority VARCHAR(100) NOT NULL,
    CONSTRAINT uk_roles_authority UNIQUE (authority)
);

ALTER SEQUENCE roles_seq OWNED BY roles.id;
