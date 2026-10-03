# Pendências e limites conhecidos

Este arquivo registra limites do código publicado, não uma certificação de completude.

- Compilar e executar todos os testes; não executados durante a publicação.
- Criar testes de integração MySQL, migrações, segurança, concorrência e gateway simulado.
- Revisar JDBC/Tuple, mapeamentos JPA e migrações em MySQL 8.
- Conferir/homologar contrato específico de Pix, débito, 3DS e assinaturas/retries de webhook do provedor.
- Acrescentar cache separado de idempotência se exigido; atualmente o resultado é persistido em MySQL.
- Implementar limite de corpo antes da leitura/desserialização em todos os endpoints.
- Completar expiração/exportação de dados pessoais em resultados de pagamento, outbox/dead-letter, consentimentos e demais cópias. O fluxo atual não representa atendimento integral à LGPD.
- Revisar ordem de locks e retries de deadlock em concorrência entre reserva/finalização/webhook.
- Revisar se consulta de produto precisa de bloqueio/versionamento para preço alterado durante a finalização.
- SMTP tem semântica de entrega pelo menos uma vez; falha após envio pode gerar email duplicado.
- Criação com resultado desconhecido não faz nova cobrança indiscriminada; ausência persistente na busca leva à dead-letter. Falta ferramenta administrativa autenticada para análise/reprocessamento.
- Aprovação tardia depois de status terminal ou de liberação de cupom requer política de reconciliação explícita.
- Reembolso total obtido antes da notificação de aprovação não agenda email de aprovação. Definir a política de comunicação.
- CVV local valida formato, não autenticidade. Dados brutos de cartão não entram no endpoint tokenizado.
- Base de frete é interna; não inclui empacotamento físico ou tabela real de transportadoras.
- Geolocalização é opcional e depende de preenchimento confiável da tabela; não há provedor de geolocalização externo.
- Ajustar exceções de catálogo, documentação e demais rotas na configuração de segurança ao integrar com ecommerce existente.
- Completar OpenAPI e política de privacidade específica da operação.
- A base independente adiciona somente catálogo mínimo. Não contém autenticação emissora JWT nem frontend de tokenização/3DS.
- Nenhuma alegação de PCI-DSS, certificação, homologação ou prontidão para produção.

Não adicionar credenciais, tokens reais, dados de clientes ou certificados a este repositório.
