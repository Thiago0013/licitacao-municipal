# Licitação Municipal — MVP Sistema de Defesa TCE

## Como rodar

### Pré-requisitos
- Java 21
- Maven 3.8+

### Configurar
1. Abra `src/main/resources/application.properties`
2. Preencha:
   - `gemini.api.key` → sua chave da API do Gemini Pro
   - `tce.pdf.url` → URL do PDF diário do TCE

3. Coloque o PDF da defesa aprovada em:
   ```
   src/main/resources/defesa-aprovada.pdf
   ```

### Rodar
```bash
mvn spring-boot:run
```

---

## Endpoints

| Método | URL | Descrição |
|--------|-----|-----------|
| GET | `/api/health` | Health check |
| POST | `/api/analisar` | Envia PDF da declaração para análise |
| GET | `/api/historico` | Lista análises anteriores |
| GET | `/api/historico/{id}` | Busca análise por ID |
| POST | `/api/tce/baixar` | Força download do PDF do TCE agora |

### Exemplo de chamada (Postman / curl)
```bash
curl -X POST http://localhost:8080/api/analisar \
  -F "arquivo=@/caminho/para/declaracao.pdf"
```

---

## Banco de dados (H2)
Acesse o console em: http://localhost:8080/h2-console  
JDBC URL: `jdbc:h2:mem:licitacaodb`  
Usuário: `sa` | Senha: *(vazio)*

---

## Estrutura do projeto
```
src/main/java/com/licitacao/municipal/
├── controller/
│   └── AnaliseController.java     ← Endpoints REST
├── service/
│   ├── AnaliseService.java        ← Orquestrador principal
│   ├── GeminiService.java         ← Integração Gemini Pro
│   ├── PdfExtractorService.java   ← Extração de texto dos PDFs
│   └── TceService.java            ← Download PDF do TCE
├── model/
│   └── Analise.java               ← Entidade JPA
├── repository/
│   └── AnaliseRepository.java     ← Repositório H2
├── config/
│   └── WebClientConfig.java       ← Config WebClient
└── LicitacaoMunicipalApplication.java
```
