# CLAUDE.md - Spring Boot Backend Engineering Standards

## 1. Core Build & Execution Commands
- **Compile & Build**: `./mvnw clean package -DskipTests` (or `gradlew build -x test`)
- **Run Locally**: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
- **Run Tests**: `./mvnw test` (Unit) | `./mvnw verify` (Integration)
- **Code Formatting**: `./mvnw spotless:apply` (Must be run before committing)
- **Dependency Check**: `./mvnw dependency:analyze`

## 2. Architectural Guidelines (Clean Architecture)
Strictly adhere to a layered architecture to ensure separation of concerns:
- **API/Controller Layer (`.controller`)**: 
  - Exclusively for HTTP routing, parameter validation (`@Valid`), and response wrapping.
  - **Zero business logic** allowed here.
- **Service Layer (`.service`)**: 
  - Core business logic resides here. 
  - Must use interface-driven design (`IService` and `ServiceImpl`).
- **Data Access Layer (`.mapper` / `.repository`)**: 
  - Strictly for database interactions (MyBatis-Plus or Spring Data JPA).
- **Domain/Model Layer (`.model`)**:
  - **Entity**: Direct database mapping.
  - **DTO**: Data Transfer Objects for Service <-> Controller communication. Prefer Java `record`.
  - **VO**: View Objects for Controller <-> Client communication.

## 3. Coding Style & Syntax (Java 17/21+)
- **Dependency Injection**: **Constructor Injection is mandatory**. Do not use `@Autowired` on fields. Use Lombok's `@RequiredArgsConstructor`.
- **Boilerplate Reduction**: Extensively use Lombok (`@Data`, `@Builder`, `@Slf4j`) to keep classes clean.
- **Null Safety**: Wrap potentially null return values in `Optional<T>`. Avoid returning `null` explicitly.
- **Stream API**: Use Java Streams for complex collection filtering, mapping, and reductions.
- **Exception Handling**: 
  - Never throw generic `Exception` or `RuntimeException`.
  - Throw specific custom exceptions (e.g., `BusinessException`, `ResourceNotFoundException`).
  - Use `@RestControllerAdvice` for global, unified error response formatting.

## 4. Database & Persistence Standards (MySQL)
- **Table Naming Convention**: All business-related database tables MUST use the `breath_` prefix (e.g., `breath_user`, `breath_goal_record`).
- **Schema Migrations**: 
  - Manual database modifications are strictly prohibited.
  - All DDL/DML changes must be versioned via Flyway or Liquibase scripts in `src/main/resources/db/migration`.
- **Transaction Management**: 
  - Apply `@Transactional` only at the Service layer.
  - Always specify `rollbackFor = Exception.class`.
  - Use `@Transactional(readOnly = true)` for pure query methods to optimize performance.

## 5. Messaging & Asynchronous Processing (RabbitMQ)
- **Reliability**: All message publishers must implement publisher confirms and returns.
- **Consumption**: Consumers must use manual acknowledgment (`basicAck`, `basicNack`).
- **Failure Handling**: Dead Letter Exchanges (DLX) and Dead Letter Queues (DLQ) must be configured for all critical business queues to prevent message loss.

## 6. Testing & Quality Assurance
- **Frameworks**: JUnit 5 + Mockito.
- **Naming Convention**: Use descriptive test method names: `should_[ExpectedBehavior]_when_[StateUnderTest]`.
- **Integration Tests**: Use **Testcontainers** to spin up ephemeral MySQL and RabbitMQ Docker containers for realistic integration testing. Do not use H2 for tests if production is MySQL.

## 7. Claude Code Directives
When executing modifications in this repository, Claude must:
1. Always analyze the impact on the database schema before modifying entities.
2. Ensure any new Spring components are properly registered and injected via constructors.
3. Automatically run `./mvnw spotless:apply` after writing Java code.
4. Generate Conventional Commit messages (e.g., `feat(goals): add breath_goal persistence layer`).