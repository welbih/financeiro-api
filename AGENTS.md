# Arquitetura do projeto

Este sistema segue arquitetura em camadas, com pacote base
`com.datadynecode.financeiro.api`.

## Camadas e responsabilidades

- `controller`: endpoints HTTP, validacao das entradas e definicao das respostas.
- `service`: regras de negocio, coordenacao dos casos de uso e limites transacionais.
- `repository`: acesso ao banco de dados por meio do Spring Data JPA.
- `model`: entidades JPA e tipos do dominio.
- `dto`: contratos de entrada e saida da API, separados das entidades.
- `mapper`: conversao entre entidades e DTOs, quando necessaria.
- `config`: configuracoes tecnicas do Spring e da aplicacao.
- `exception`: excecoes da aplicacao e tratamento centralizado de erros HTTP.

## Regras de implementacao

- O fluxo principal e `controller -> service -> repository`.
- Controllers nao acessam repositories diretamente e nao implementam regras de negocio.
- Services nao dependem de controllers nem de tipos HTTP, como `ResponseEntity`.
- Repositories nao dependem de services ou controllers.
- Entidades nao devem ser expostas diretamente nos contratos HTTP; use DTOs.
- Use injecao de dependencias por construtor.
- Declare transacoes na camada de service quando o caso de uso exigir.
- Mantenha configuracoes de ambiente fora do codigo e credenciais fora do controle de versao.
- Crie pacotes e classes conforme as funcionalidades forem implementadas; nao crie
  interfaces ou classes vazias apenas para representar camadas.
- Nos testes, verifique regras de negocio nos services e contratos HTTP nos controllers;
  use testes de integracao quando for necessario validar persistencia e transacoes.
