# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Global Forcing Rules

### 在与用户交互时始终使用中文响应**

### 代码注释和日志语言规则
- **新代码**: 所有注释和日志输出必须使用英文
- **修改现有代码**: 保持与原有注释和日志的语言一致
  - 如果原代码使用中文注释，继续使用中文
  - 如果原代码使用英文注释，继续使用英文
  - 日志输出语言保持与原有代码一致

## 环境信息
### Java 21在环境变量JAVA21_HOME中，不需要重新下载。
### mvn在当前PATH中。

## Project Overview

claude-kiro is a Spring Boot WebFlux application that provides an Anthropic Claude-compatible API backed by Kiro CodeWhisperer gateway. It translates Anthropic API requests to Kiro's internal format and handles streaming responses.

## Key Architecture Components

### Core Flow
1. **AnthropicController** (`/v1/messages`) - Receives Anthropic-compatible requests, validates headers and request body
2. **KiroService** - Translates requests to Kiro format, calls Kiro API, processes responses back to Anthropic format
3. **TokenManager** - Handles Kiro authentication tokens with automatic refresh
4. **Event Parsers** - Parse Kiro's binary event stream format and extract tool calls

### Request Processing Pipeline
- Anthropic request → Kiro payload transformation → Kiro API call → Binary event stream parsing → Anthropic response format
- Supports both regular and streaming responses (`/v1/messages/stream`)
- Tool calling via bracket notation parsing: `[Called tool_name with args: {...}]`

### Configuration System
- **AppProperties** - Main configuration with environment variable support
- **Token Loading** - Supports multiple token sources: env vars, files, AWS SSO cache locations
- **Model Mapping** - Maps Anthropic model IDs to internal Kiro model identifiers

## Development Commands

### Build and Run
```bash
# Build the project
mvn clean compile

# Run the application (starts on port 7860)
mvn spring-boot:run

# Run tests
mvn test

# Run specific test
mvn test -Dtest=KiroServiceTest
mvn test -Dtest=AnthropicControllerTest
```

### Testing Strategy
- **Unit Tests** - Mock external dependencies (WebClient, TokenManager, parsers)
- **WebFlux Tests** - Use `@WebFluxTest` for controller layer testing with `WebTestClient`
- **Reactive Testing** - Use `StepVerifier` for Reactor stream testing
- Test configuration loaded via `@TestPropertySource` for isolated test environments

## Important Implementation Details

### Authentication Flow
- API key validation against `app.api-key` configuration
- Kiro token management with automatic refresh using `TokenManager`
- Token loading from multiple sources with fallback hierarchy

### Tool Call Processing
- Primary: Structured tool calls from Kiro event JSON
- Secondary: Bracket notation parsing from `rawText` fields
- Deduplication via `ToolCallDeduplicator` to prevent duplicate tool calls

### Streaming Implementation
- Server-Sent Events (SSE) format for Anthropic compatibility
- Event sequence: `message_start` → `content_block_start/delta/stop` → `message_delta` → `message_stop`
- Binary event stream parsing from Kiro's proprietary format

### Error Handling
- Token refresh on API failures with retry logic
- Request validation with detailed error messages
- Global exception handling via `GlobalExceptionHandler`

## Configuration

### Environment Variables
- `API_KEY` - Anthropic API key for client authentication
- `ANTHROPIC_VERSION` - API version (default: 2023-06-01)
- `KIRO_BASE_URL` - Kiro gateway endpoint
- `KIRO_PROFILE_ARN` - AWS profile ARN for Kiro
- `KIRO_ACCESS_TOKEN` / `KIRO_REFRESH_TOKEN` - Authentication tokens
- `KIRO_TOKEN_PATH` / `KIRO_REFRESH_TOKEN_PATH` - Token file paths

### Application Properties
- Main configuration in `application.yml`
- Supports Spring Boot's property binding and validation
- Token file loading with OS-specific cache paths

## Model Support

Current model mappings (defined in `KiroService.mapModel()`):
- `claude-sonnet-4-5-20250929` → `CLAUDE_SONNET_4_5_20250929_V1_0`
- `claude-3-5-haiku-20241022` → `auto`
- Default fallback to `CLAUDE_SONNET_4_5_20250929_V1_0`

## Development Notes

### Reactive Programming
- Built on Spring WebFlux with Reactor (Mono/Flux)
- Non-blocking I/O throughout the stack
- Proper timeout handling (120s for Kiro API calls)

### Dependencies
- Spring Boot 3.3.13 with WebFlux
- Jackson for JSON processing
- Apache Commons Lang3 and Text for utilities
- Lombok for reducing boilerplate code

### Code Organization
- `controller/` - REST API endpoints
- `service/` - Business logic and external API integration
- `model/` - Request/response DTOs
- `parser/` - Event parsing and tool call extraction
- `config/` - Configuration properties and WebClient setup
- 对于需要访问Kiro CodeWhisperer gateway的集成测试,直接运行主程序,不要使用mock
- Kiro CodeWhisperer gateway返回的响应多打印日志以便问题排查
- 每次运行e2e测试前注意检查主程序是否已经在后台运行，如果没有运行，则要在后台运行起来