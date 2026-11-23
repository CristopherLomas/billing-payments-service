# Billing & Payments Service (`billing-payments-service`)

> **Corredor Norte - Sistema de Transporte Interprovincial**  
> **Grupo 5:** Módulo de Pagos, Conciliación Financiera y Facturación

---

## 1. Descripción General

El microservicio `billing-payments-service` gestiona de manera centralizada el ciclo de vida de los pagos del Sistema Corredor Norte. Permite a los pasajeros registrar comprobantes bancarios, transferencias, pagos con DeUna! o confirmar reservas en efectivo para cobro al abordar, ofreciendo a los administradores una bandeja de verificación, auditoría inmutable, conciliación diaria y reportes financieros de recaudación.

### Reglas Estrictas de Arquitectura Financiera
1. **Java 21 LTS & Spring Boot 3.3+**: Uso de características modernas de Java (records, sealed patterns, switch expressions).
2. **Precisión Financiera**: Todos los montos monetarios usan estrictamente `BigDecimal` con escala a 2 decimales (`DECIMAL(10,2)`). Se prohíbe el uso de `Double` o `Float`.
3. **Trazabilidad Temporal UTC**: Todas las marcas de tiempo se almacenan y exponen como `OffsetDateTime` en UTC (ISO-8601 con sufijo `Z`).
4. **Persistencia e Integridad**: Microsoft SQL Server 2022 con control estricto de esquema vía **Flyway** (`ddl-auto: validate`). Se previene el uso de comprobantes duplicados mediante un índice único filtrado para transferencias y DeUna!.
5. **Auditoría Inmutable**: Todo cambio de estado (`PENDING_VERIFICATION`, `PENDING_CASH`, `PAID`, `REJECTED`) genera automáticamente un registro histórico en la tabla `payment_audit_logs`.
6. **Desacoplamiento de Integraciones**: Comunicación con otros microservicios (emisión de boleto QR en Grupo 4 / liberación de asientos en Grupo 3) a través de una interfaz desacoplada con switch `integration.mock` (simulación local estructurada o HTTP vía `RestClient`).
7. **Estándar de Errores RFC 7807**: Respuestas de error uniformes basadas en `ProblemDetail`.
8. **Seguridad Stateless JWT**: Autenticación Bearer token con control de acceso basado en roles (`ROLE_PASAJERO`, `ROLE_ADMIN`).

---

## 2. Requisitos Previos

- **Opción A (Recomendada con Docker)**:
  - Docker Engine 24+ y Docker Compose v2+
- **Opción B (Ejecución Local Nativa)**:
  - JDK 21 (Amazon Corretto, Eclipse Temurin o Microsoft Build of OpenJDK)
  - Apache Maven 3.9+
  - Instancia de SQL Server 2022 accesible

---

## 3. Despliegue Rápido con Docker Compose

Para compilar y levantar todo el ecosistema (SQL Server 2022, inicialización automática de `Payments_DB`, migraciones de Flyway y el microservicio empaquetado) con un solo comando:

```bash
docker compose up --build
```

Una vez levantado:
- **Swagger UI (Documentación interactiva)**: [http://localhost:8085/swagger-ui.html](http://localhost:8085/swagger-ui.html)
- **OpenAPI JSON Spec**: [http://localhost:8085/v3/api-docs](http://localhost:8085/v3/api-docs)
- **Actuator Health**: [http://localhost:8085/actuator/health](http://localhost:8085/actuator/health)

Para detener los servicios:
```bash
docker compose down
```

---

## 4. Variables de Entorno

| Variable | Descripción | Valor por Defecto |
| :--- | :--- | :--- |
| `SERVER_PORT` | Puerto HTTP expuesto por la aplicación | `8085` |
| `SPRING_DATASOURCE_URL` | Cadena JDBC de conexión a SQL Server | `jdbc:sqlserver://localhost:1433;databaseName=Payments_DB;encrypt=true;trustServerCertificate=true` |
| `SPRING_DATASOURCE_USERNAME` | Usuario de base de datos | `sa` |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña de base de datos | `Payments_2026!Secure` |
| `INTEGRATION_MOCK` | Activa (`true`) o desactiva (`false`) el modo simulación para llamadas a otros microservicios | `true` |
| `QR_SERVICE_URL` | URL base del microservicio de emisión de Boletos QR | `http://localhost:8081` |
| `BOOKING_SERVICE_URL` | URL base del microservicio de Reservas / Asientos | `http://localhost:8082` |
| `JWT_SECRET` | Clave secreta HMAC-SHA256 para validación de tokens JWT | *(Clave por defecto de desarrollo)* |
| `JWT_DEV_MODE` | Habilita endpoint local `/api/v1/dev/token` para pruebas sin depender de Auth | `true` |

---

## 5. Autenticación y Pruebas en Desarrollo

Cuando `JWT_DEV_MODE=true`, puedes obtener tokens JWT válidos al instante para probar la API:

### Obtener Token como Pasajero
```bash
curl -X POST "http://localhost:8085/api/v1/dev/token?userId=usr-pasajero-1&role=PASAJERO"
```

### Obtener Token como Administrador
```bash
curl -X POST "http://localhost:8085/api/v1/dev/token?userId=adm-taquilla-1&role=ADMIN"
```

Usa el token recibido en la cabecera HTTP de todas las peticiones protegidas:
```http
Authorization: Bearer <token>
```

---

## 6. Catálogo de Endpoints REST

### 6.1. Endpoints para Pasajeros (`/api/v1/payments`)
*Requiere rol `ROLE_PASAJERO` o `ROLE_ADMIN`.*

| Método | Ruta | Descripción | Estado HTTP |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/payments` | Registra un nuevo pago (transferencia, DeUna! o efectivo) | `201 Created` |
| `GET` | `/api/v1/payments/{id}` | Consulta el estado actual de un pago específico (para polling) | `200 OK` |
| `GET` | `/api/v1/payments/my-payments` | Lista paginada del historial de pagos del usuario autenticado | `200 OK` |

#### Ejemplo de Creación de Pago (`POST /api/v1/payments`)
```json
{
  "reservationId": "RES-8940",
  "tripId": "TRIP-2026-001",
  "amount": 14.50,
  "paymentMethod": "DEUNA",
  "referenceNumber": "DEUNA-982143"
}
```

---

### 6.2. Endpoints para Administradores (`/api/v1/admin/payments`)
*Requiere rol `ROLE_ADMIN`.*

| Método | Ruta | Descripción | Estado HTTP |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/admin/payments/pending` | Bandeja de pagos pendientes por verificar (FIFO). Acepta `?method=DEUNA` | `200 OK` |
| `PATCH` | `/api/v1/admin/payments/{id}/approve` | Aprueba el pago, cambia estado a `PAID` y activa el boleto QR | `200 OK` |
| `PATCH` | `/api/v1/admin/payments/{id}/reject` | Rechaza el comprobante con motivo, pasa a `REJECTED` y libera asiento | `200 OK` |
| `GET` | `/api/v1/admin/payments/{id}/audit` | Historial cronológico inmutable de auditoría del pago | `200 OK` |

#### Ejemplo de Aprobación (`PATCH /api/v1/admin/payments/{id}/approve`)
```json
{
  "notes": "Comprobante verificado exitosamente en portal bancario"
}
```

#### Ejemplo de Rechazo (`PATCH /api/v1/admin/payments/{id}/reject`)
```json
{
  "reason": "Monto transferido no coincide con el valor de la reserva ($14.50)"
}
```

---

### 6.3. Reportes y Conciliación Financiera (`/api/v1/admin/payments`)
*Requiere rol `ROLE_ADMIN`.*

| Método | Ruta | Parámetros | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/admin/payments/reports/revenue` | `from={ISO-8601}&to={ISO-8601}` | Reporte financiero de recaudación total, desglose por método y transacciones |
| `GET` | `/api/v1/admin/payments/reconciliation` | `date={YYYY-MM-DD}` (opcional, por defecto hoy) | Conciliación diaria (cuadre de caja) con detalle de pagos |

---

## 7. Ejecución de Pruebas Unitarias y de Integración

El proyecto cuenta con cobertura exhaustiva de pruebas unitarias sobre los servicios de negocio, máquina de estados, validaciones matemáticas con `BigDecimal`, autorización por roles y controladores REST:

```bash
mvn test
```

Para empaquetar el proyecto localmente sin ejecutar tests:
```bash
mvn clean package -DskipTests
```

---

## 8. Estructura del Proyecto

```
billing-payments-service/
├── docker/
│   └── init-db.sql                 # Script de creación idempotente de Payments_DB
├── src/
│   ├── main/
│   │   ├── java/com/corredornorte/payments/
│   │   │   ├── config/             # Configuración JWT, Security, RestClient, OpenAPI
│   │   │   ├── controller/         # PaymentController, AdminPaymentController, ReportController
│   │   │   ├── dto/                # Records Java 21 para Request/Response
│   │   │   ├── entity/             # Entidades JPA (Payment, PaymentAuditLog) y Enums
│   │   │   ├── exception/          # Excepciones de dominio y GlobalExceptionHandler (RFC 7807)
│   │   │   ├── integration/        # ExternalBookingGateway (Mock y RestClient HTTP)
│   │   │   ├── repository/         # Repositorios Spring Data JPA con agregaciones JPQL
│   │   │   └── service/            # Lógica de estados, verificación, auditoría y reportes
│   │   └── resources/
│   │       ├── db/migration/       # Migraciones Flyway SQL Server (V1__init_payments_schema.sql)
│   │       └── application.yml     # Configuración centralizada con variables de entorno
│   └── test/java/com/corredornorte/payments/
│       ├── controller/             # Pruebas de integración MockMvc para endpoints REST
│       ├── security/               # Pruebas de autorización RBAC (ROLE_PASAJERO, ROLE_ADMIN)
│       └── service/                # Pruebas unitarias de PaymentService y ReportService
├── docker-compose.yml              # Orquestación de SQL Server 2022 y el microservicio
├── Dockerfile                      # Empaquetado multi-stage (Maven Temurin 21 -> Temurin 21 JRE Alpine)
└── pom.xml                         # Dependencias Maven (Spring Boot 3.3.5, Java 21)
```
