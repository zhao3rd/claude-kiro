# claude-kiro

Anthropic Claude-compatible API service backed by Kiro CodeWhisperer gateway.

## Features
- **Compatible Interface**: Provides interface definitions compatible with Anthropic Claude API, facilitating integration with existing clients.
- **High-Performance Gateway**: Relies on Kiro CodeWhisperer gateway for request forwarding and load management.
- **Spring Boot Architecture**: Built on Spring Boot WebFlux, supporting reactive programming model.
- **Observability**: Built-in Actuator endpoints for easy monitoring and maintenance.

## API Compatibility and Adaptations
This service acts as an adapter to the backend Kiro Gateway, not a transparent proxy. As such, some fields from the standard Anthropic Claude API request are intentionally ignored because they are not supported by the Kiro Gateway.

The following request parameters are **ignored**:
- `temperature`
- `top_p`
- `top_k`
- `max_tokens`
- `metadata`

Additionally, image data is not forwarded; only a textual placeholder for the image is sent to the backend.

## Requirements
- Java 21
- Maven 3.9 or above

## Quick Start
1. Clone the repository:

   ```bash
   git clone <repo-url>
   cd claude-kiro
   ```

2. Build and run:

   ```bash
   mvn spring-boot:run
   ```

3. By default, the service will start on `http://localhost:8080`. Configure the backend Kiro gateway parameters according to your business needs.

## Configuration
Main configuration items are located in `application.yml` or `application.properties`, where you can adjust API keys, gateway addresses, and timeout settings based on your environment.

## Development and Testing
- Run unit tests:

  ```bash
  mvn test
  ```

- It is recommended to use your IDE's Spring Boot run configuration for debugging.

## License
This project is licensed under the [MIT License](LICENSE).

