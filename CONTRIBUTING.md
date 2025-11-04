# Contributing to RouteForge

Thank you for your interest in contributing to RouteForge! This document provides guidelines for contributing to the project.

## Getting Started

1. **Fork the repository** and clone it locally
2. **Set up your development environment:**
   ```bash
   # Install Java 17+
   # Install Docker and Docker Compose
   
   # Copy environment configuration
   cp .env.example .env
   
   # Start infrastructure
   docker compose up -d
   
   # Build the project
   ./gradlew build
   ```

3. **Create a feature branch:**
   ```bash
   git checkout -b feature/your-feature-name
   ```

## Development Guidelines

### Code Style
- Follow Java standard conventions
- Use meaningful variable and method names
- Keep methods focused and concise (single responsibility)
- Add JavaDoc comments for public APIs
- Use Lombok annotations to reduce boilerplate

### Testing
- Write unit tests for all business logic
- Use Testcontainers for integration tests
- Aim for >80% code coverage
- Run tests before committing: `./gradlew test`

### Commits
- Write clear, descriptive commit messages
- Use present tense ("Add feature" not "Added feature")
- Reference issue numbers when applicable
- Keep commits atomic and focused

### Pull Requests
1. Ensure all tests pass
2. Update documentation as needed
3. Add a clear description of your changes
4. Link to any related issues
5. Request review from maintainers

## Project Structure

```
routeforge/
├── routeforge-common/       # Shared DTOs and utilities
├── ingestion-service/       # GTFS-RT ingestion
├── processing-service/      # Event processing
├── api-gateway-service/     # REST and WebSocket APIs
├── infra/                   # Infrastructure configs
└── scripts/                 # Utility scripts
```

## Running Services

```bash
# Start all services
docker compose up

# Run individual service
./gradlew :ingestion-service:bootRun

# Run tests
./gradlew test

# Run integration tests
./gradlew integrationTest
```

## Reporting Issues

- Use the GitHub issue tracker
- Provide a clear description
- Include steps to reproduce
- Share relevant logs and error messages
- Specify your environment (OS, Java version, etc.)

## Questions?

Feel free to open an issue for questions or discussions!
