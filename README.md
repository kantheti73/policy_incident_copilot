# Policy & Incident Copilot

An intelligent internal assistant that helps employees answer policy/procedure questions and perform first-pass incident triage using **LangChain4J**, **LangGraph4J**, **Pinecone**, **Neo4J**, and **Prometheus**.

## Architecture Overview

```
                    +-------------------+
                    |   REST API        |
                    |  /api/v1/copilot  |
                    +--------+----------+
                             |
                    +--------v----------+
                    |  Guardrails Engine |
                    |  (Input Sanitize)  |
                    +--------+----------+
                             |
                    +--------v----------+
                    |   Agent Router     |
                    | (Complexity Check) |
                    +----+--------+-----+
                         |        |
              SINGLE     |        |    MULTI
         +-------v---+   |   +---v-----------+
         | Policy     |   |   | Planner       |
         | Query      |   |   +-------+-------+
         | Agent      |   |           |
         +-------+----+   |   +-------v-------+
                 |        |   | Researcher    |
                 |        |   +-------+-------+
                 |        |           |
                 |        |   +-------v-------+
                 |        |   | Orchestrator  |
                 |        |   +-------+-------+
                 |        |           |
                 |        |   +-------v-------+
                 |        |   | Critic/       |
                 |        |   | Verifier      |
                 |        |   +-------+-------+
                 |        |           |
                 +--------+----+------+
                               |
                    +----------v--------+
                    |  Guardrails Engine  |
                    |  (Output Sanitize)  |
                    +----------+---------+
                               |
                    +----------v---------+
                    |     Response        |
                    +--------------------+
```

## Key Features

### Dual-Mode Operation
- **Single-Agent Mode**: Low-latency responses for simple policy Q&A with citations
- **Multi-Agent Mode**: Complex incident triage with Planner -> Researcher -> Orchestrator -> Critic pipeline

### Intelligent Routing
- Automatic complexity classification based on query length, risk keywords, log presence, and multi-question detection
- Configurable threshold with manual override support

### Guardrails & Safety
- **Prompt Injection Detection**: Pattern-based + heuristic detection of jailbreaks, role hijacking, instruction overrides
- **PII Redaction**: Configurable regex patterns for SSN, credit cards, emails, phone numbers
- **Safety Reviewer**: Blocks requests to disable MFA, bypass policies, escalate privileges, expose credentials
- **Critic Agent**: Final safety review in multi-agent mode before response delivery

### Retrieval-Augmented Generation
- **Pinecone Vector Store**: Semantic search over policy document embeddings
- **Neo4J Knowledge Graph**: Policy relationships, dependencies, incident patterns, escalation paths
- **Document Chunker**: Recursive splitting with overlap for context preservation

### Memory Management
- Per-session conversation memory with configurable turn limits
- Safe memory filter: automatically strips passwords, tokens, API keys before storage

### Observability (Prometheus + Micrometer)
- Request latency histograms (p50, p95, p99)
- Single vs multi-agent request counters
- Guardrail trigger counts
- Prompt injection block rates
- PII redaction events
- Error rates
- Distributed trace IDs in all log entries

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 3.3 |
| LLM Integration | LangChain4J 0.36 |
| Agent Orchestration | LangGraph4J 1.5 |
| Vector Database | Pinecone (via LangChain4J) |
| Graph Database | Neo4J 5 (Spring Data) |
| Embeddings | all-MiniLM-L6-v2 (local ONNX) |
| Observability | Micrometer + Prometheus |
| Dashboards | Grafana |
| Containerization | Docker Compose |
| Language | Java 21 |

## Project Structure

```
src/main/java/com/copilot/
├── PolicyIncidentCopilotApplication.java    # Entry point
├── config/
│   ├── LangChainConfig.java                # LLM + embeddings + vector store
│   └── Neo4jConfig.java                    # Graph database
├── controller/
│   └── CopilotController.java              # REST API endpoints
├── service/
│   ├── CopilotService.java                 # Core orchestration logic
│   └── PolicyIngestionService.java         # Document ingestion pipeline
├── agent/
│   ├── router/
│   │   ├── AgentRouter.java                # Single vs multi-agent routing
│   │   └── ComplexityClassifier.java       # Complexity/risk scoring
│   ├── single/
│   │   └── PolicyQueryAgent.java           # Simple policy Q&A agent
│   └── multi/
│       ├── PlannerAgent.java               # Task decomposition
│       ├── ResearcherAgent.java            # Information retrieval + analysis
│       ├── OrchestratorAgent.java          # Synthesis + triage ticket generation
│       └── CriticAgent.java               # Safety verification
├── graph/
│   ├── SingleAgentGraph.java               # LangGraph4J single-agent workflow
│   └── MultiAgentGraph.java                # LangGraph4J multi-agent workflow
├── retrieval/
│   ├── PolicyRetriever.java                # Unified retrieval interface
│   ├── PineconeVectorStore.java            # Vector similarity search
│   ├── Neo4jKnowledgeGraph.java            # Graph traversal queries
│   └── DocumentChunker.java               # Document splitting
├── guardrails/
│   ├── GuardrailsEngine.java              # Central guardrails orchestrator
│   ├── PromptInjectionDetector.java       # Injection pattern detection
│   ├── PIIRedactor.java                   # PII pattern redaction
│   └── SafetyReviewer.java               # Safety policy enforcement
├── memory/
│   ├── ConversationMemory.java            # Session-based memory store
│   └── SafeMemoryFilter.java             # Sensitive content filter
├── observability/
│   ├── TraceContext.java                  # Distributed trace management
│   └── MetricsCollector.java             # Prometheus metrics
└── model/
    ├── CopilotRequest.java                # API request model
    ├── CopilotResponse.java               # API response with citations
    ├── GraphState.java                    # Shared agent state
    ├── PolicySnippet.java                 # Retrieved policy fragment
    └── TriageTicket.java                  # Incident triage output
```

## Getting Started

### Prerequisites
- Java 21+
- Docker & Docker Compose
- Anthropic API key (or OpenAI)
- Pinecone API key (optional — falls back to in-memory store)

### Quick Start

1. **Clone the repository**
   ```bash
   git clone https://github.com/kantheti73/policy_incident_copilot.git
   cd policy_incident_copilot
   ```

2. **Set environment variables**
   ```bash
   export ANTHROPIC_API_KEY=your-api-key
   export PINECONE_API_KEY=your-pinecone-key       # optional
   export PINECONE_ENVIRONMENT=us-east-1            # optional
   ```

3. **Start infrastructure (Neo4J, Prometheus, Grafana)**
   ```bash
   docker-compose up -d neo4j prometheus grafana
   ```

4. **Run the application**
   ```bash
   ./mvnw spring-boot:run
   ```

5. **Or run everything with Docker**
   ```bash
   docker-compose up --build
   ```

### API Usage

**Simple Policy Query (Single-Agent)**
```bash
curl -X POST http://localhost:8080/api/v1/copilot/query \
  -H "Content-Type: application/json" \
  -d '{
    "query": "What is the current password reset process?",
    "userId": "emp-123",
    "sessionId": "session-abc"
  }'
```

**Incident Triage (Multi-Agent)**
```bash
curl -X POST http://localhost:8080/api/v1/copilot/query \
  -H "Content-Type: application/json" \
  -d '{
    "query": "VPN failing for 200 users since maintenance window",
    "userId": "oncall-456",
    "sessionId": "session-xyz",
    "rawLogs": "2024-01-15 10:00:00 ERROR vpn-gateway Connection timeout for user=jdoe\n..."
  }'
```

**Ingest a Policy Document**
```bash
curl -X POST http://localhost:8080/api/v1/copilot/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "documentId": "POL-001",
    "title": "Password Reset Policy",
    "content": "All employees must reset passwords every 90 days...",
    "version": "2.1",
    "effectiveDate": "2024-01-01",
    "category": "Security"
  }'
```

### Monitoring

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)
- **Neo4J Browser**: http://localhost:7474
- **Actuator Metrics**: http://localhost:8080/actuator/prometheus

## Configuration

All configuration is in `src/main/resources/application.yml`. Key settings:

| Property | Description | Default |
|----------|-------------|---------|
| `copilot.routing.complexity-threshold` | Score threshold for multi-agent routing | `0.6` |
| `copilot.guardrails.max-steps-single-agent` | Max LLM calls in single-agent mode | `3` |
| `copilot.guardrails.max-steps-multi-agent` | Max LLM calls in multi-agent mode | `10` |
| `copilot.memory.max-conversation-turns` | Max conversation history per session | `20` |
| `copilot.retry.max-attempts` | Retry attempts for LLM calls | `3` |

## Testing

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=PromptInjectionDetectorTest
```

## License

MIT
