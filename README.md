# 🎟️ App Ticketing Nequi - Sistema Reactivo de Compra de Entradas

Sistema de alto rendimiento y alta concurrencia desarrollado con **Java 25**, **Spring Boot 4.1.1**, **Spring WebFlux**, **AWS DynamoDB** y **Amazon SQS** siguiendo la arquitectura limpia (**Bancolombia Clean Architecture**).

---

## 🏗️ Arquitectura y Decisiones de Diseño

1. **Flujo de Control Limpio:**
   `RouterRest` ➔ `Handler` ➔ `ValidationHelper` ➔ `UseCase` ➔ `Adapters` (DynamoDB / SQS).

2. **Garantía de No Sobreventa (Optimistic Locking & Conditional Writes):**
   Las reservas de entradas se realizan mediante escrituras condicionales en DynamoDB (`availableCapacity >= quantity`), evitando *race conditions* bajo alta carga.

3. **Procesamiento Asíncrono de Compras (SQS):**
   Las compras emiten un evento a la cola SQS de procesamiento asíncrono devolviendo de inmediato una orden en estado `PENDING_CONFIRMATION` (HTTP 202 Accepted). Un consumidor reactivo asíncrono procesa la orden y la confirma como `SOLD`.

4. **Resiliencia (Resilience4j):**
   Configuración centralizada en `application.yaml` para registrar únicamente errores de infraestructura (`TechnicalException`) e ignorar excepciones de negocio o validación (`BusinessException`).

5. **Estructura de Respuesta Estandarizada (`ResponseEnvelope<T>`):**
   ```json
   {
     "statusCode": "0",
     "description": "Operation completed successfully",
     "data": { ... }
   }
   ```

---

## 🚀 Instalación y Despliegue

### Prerrequisitos
* **Docker** y **Docker Compose**
* **Java 25** (para desarrollo local)

### Levantar Todo el Entorno con Docker Compose

```powershell
docker-compose up --build -d
```

Este comando iniciará:
* **LocalStack**: Emulando DynamoDB (tablas `events` y `orders`) y SQS (`ticket-purchase-queue`).
* **App Ticketing Nequi**: Microservicio reactivo en el puerto `8080`.

---

## 📡 Ejemplos de Peticiones (cURL)

### 1. Crear un Evento
```bash
curl -X POST http://localhost:8080/api/v1/events \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Concierto Shakira 2026",
    "date": "2026-11-15T20:00:00Z",
    "venue": "Estadio El Campín",
    "totalCapacity": 50000,
    "ticketPrice": 150000.00
  }'
```

### 2. Consultar Eventos
```bash
curl -X GET http://localhost:8080/api/v1/events
```

### 3. Iniciar Compra de Entradas (Reserva Temporizada)
```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "<ID_DEL_EVENTO>",
    "customerId": "cust-3101234567",
    "quantity": 2
  }'
```

### 4. Consultar Estado de una Orden
```bash
curl -X GET http://localhost:8080/api/v1/orders/<ID_DE_ORDEN>
```

---

## 🧪 Pruebas Unitarias y Cobertura

Para ejecutar la suite de pruebas unitarias con Gradle:

```powershell
.\gradlew test
```

Para generar el reporte de cobertura de código (JaCoCo):

```powershell
.\gradlew jacocoMergedReport
```
El reporte HTML estará disponible en `build/reports/jacocoHtml/index.html`.
