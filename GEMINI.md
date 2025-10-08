# Project Overview

This project, `claude-kiro`, is a Java-based service that provides an Anthropic Claude-compatible API. It acts as a gateway, forwarding requests to the Kiro CodeWhisperer gateway. The service is built using Spring Boot and WebFlux, making it a reactive and high-performance application.

**Key Technologies:**

*   **Java 21:** The project is built using Java 21.
*   **Spring Boot 3.3:** The application is built on the Spring Boot framework, which simplifies the development of stand-alone, production-grade Spring-based applications.
*   **Spring WebFlux:** The service uses Spring WebFlux for reactive, non-blocking web programming.
*   **Maven:** The project is managed using Maven, a popular build automation tool.
*   **Lombok:** The project uses Lombok to reduce boilerplate code.
*   **Jackson:** The project uses Jackson for JSON serialization and deserialization.

**Architecture:**

The application is structured as a typical Spring Boot application.

*   `ClaudeKiroApplication.java`: The main entry point for the application.
*   `AnthropicController.java`: Defines the REST endpoints for the service, handling requests to `/v1/messages` and `/v1/messages/stream`.
*   `KiroService.java`: Contains the business logic for communicating with the Kiro CodeWhisperer gateway.
*   `application.yml`: The main configuration file for the application, where you can configure API keys, gateway addresses, and other settings.

# Building and Running

**Prerequisites:**

*   Java 21
*   Maven 3.9 or above
* 
**Build and Run:**

To build and run the application, use the following command:
*   java21在环境变量 JAVA21_HOME中

```bash
mvn spring-boot:run
```

The service will start on `http://localhost:7860` by default.

**Testing:**

To run the unit tests, use the following command:

```bash
mvn test
```

# Development Conventions

*   **Coding Style:** The project follows standard Java coding conventions.
*   **Testing:** The project includes unit tests and end-to-end tests. Unit tests are located in `src/test/java` and can be run with `mvn test`. End-to-end tests are located in `src/test/java/org/yanhuang/ai/e2e` and can be run with the `run-e2e-tests.ps1` script.
*   **Configuration:** The application is configured using the `application.yml` file. Properties can be overridden using environment variables.
*   **Logging:** The project uses SLF4J for logging. The logging level can be configured in the `application.yml` file.
