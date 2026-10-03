# checkout-pago-FINAL

Módulo backend de checkout em Java 17 e Spring Boot, com pedidos, cupons, frete, pagamentos e webhooks.

## Estado

Consolidação do código apresentado na conversa, com arquivos-base para um repositório independente. **Não homologado para produção.** A gravação no GitHub não significa que compilação, testes ou requisitos foram validados. Consulte `docs/PENDENCIAS.md`.

- Java 17, Maven, Spring Boot 3.1.0, EntityManager, MySQL 8, Flyway.
- API JWT (issuer, audience e escopo `checkout`).
- Frete por base interna; tarifas, CEPs e perfis devem ser cadastrados.
- Pagamento assíncrono: POST retorna 202 e consulta posterior retorna o estado.
- Tokens de cartão, não PAN/CVV, no endpoint de pagamento.
- Nenhum segredo ou certificado real está incluído.

## Execução local

1. Instale Java 17 e Maven; disponibilize um MySQL 8 vazio para este projeto.
2. Configure as variáveis listadas em `.env.example` no ambiente do processo. Spring não carrega esse arquivo automaticamente.
3. Disponibilize certificado PKCS12 e chaves criptográficas próprias.
4. Rode `mvn test`, depois `mvn spring-boot:run` para iniciar com o perfil `checkout`.
5. Cadastre produtos, CEPs, perfis e tarifas por processo administrativo autorizado. Não há valores logísticos fictícios.

Executar a aplicação com credenciais reais permite aos workers chamar o gateway. O push não inicia a aplicação e nenhuma chamada de pagamento é executada para publicar estes arquivos.

## Banco e origem

A migration `V202610030000` é um **catálogo mínimo independente**, necessário às FKs das migrations fornecidas no chat. Não é cópia integral nem migração automática do ecommerce original. Para incorporar o módulo a um banco já existente, revise essa migration e compatibilidade do catálogo antes de executar Flyway.

`EntityWithLongId` preserva o contrato de ID usado nos trechos anteriores. Este repositório contém o checkout proposto; não redistribui o restante do repositório de origem.

## Endpoints

Prefixo `/api/checkout`:

- PUT `/carrinho/itens`
- GET `/resumo`
- POST `/cupom`
- DELETE `/cupom` e POST `/cupom/remover`
- POST `/calcular-frete`
- POST `/selecionar-frete`
- GET `/frete`
- POST `/finalizar-compra` (header `Idempotency-Key: UUID`)
- GET `/pedidos/{id}`
- GET/PUT `/privacidade/dados`
- POST `/privacidade/consentimentos`
- POST `/privacidade/exclusao`

Webhook: POST `/api/webhook/pagamento`, parâmetros e headers definidos pelo adaptador Mercado Pago.

## Compatibilidade

Os códigos HTTP e nomes de alguns campos diferem dos exemplos originais, em especial o fluxo assíncrono (202), a exigência de `cotacao_id`, `id_carrinho`, consentimento e token de cartão. As dependências foram mantidas conforme os trechos anteriores; isso não representa validação de atualização ou suporte de segurança.
