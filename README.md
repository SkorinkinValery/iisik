# Система управления сервисом по ремонту бытовой техники

Распределенная учебная система реализует регистрацию клиентов и техники, оформление заявок, диагностику, заказ-наряды, учет услуг и запасных частей, платежи, уведомления и аналитику. Внешний API предоставляется через GraphQL Gateway. Внутреннее синхронное взаимодействие выполняется по gRPC, а события передаются через Kafka.

## Состав системы

| Модуль | Назначение | HTTP | gRPC |
|---|---|---:|---:|
| `graphql-gateway` | GraphQL API, JWT, проверка Redis-сессии, RBAC | 8080 | - |
| `identity-service` | Пользователи, роли, клиенты, сотрудники, вход и сессии | 8081 | 9101 |
| `device-service` | Устройства и категории устройств | 8082 | 9102 |
| `repair-request-service` | Заявки, фильтрация, сортировка и история статусов | 8083 | 9103 |
| `work-order-service` | Диагностика, заказ-наряды, услуги, детали и стоимость | 8084 | 9104 |
| `catalog-service` | Справочники услуг и деталей, остатки и резервирование | 8085 | 9105 |
| `payment-service` | Идемпотентная регистрация платежей | 8086 | 9106 |
| `notification-service` | Сохраняемые in-app уведомления | 8087 | 9107 |
| `analytics-service` | Ремонты, выручка, услуги и загрузка сотрудников | 8088 | 9108 |

`platform-contracts` генерирует Java и gRPC классы из `platform.proto` во время сборки. `service-runtime` содержит общую поддержку gRPC, correlation ID, Kafka topics, DLT и transactional outbox.

Каждый сервис данных подключается к собственной базе PostgreSQL под отдельным пользователем. Межбазовые foreign key и прямое чтение чужих таблиц отсутствуют.

## Сборка и тесты

Требуются JDK 17 или новее, Docker Desktop и Docker Compose.

```powershell
.\mvnw.cmd clean test
```

Команда собирает все модули, генерирует protobuf-классы и запускает проверки бизнес-правил, стоимости, остатков, прав доступа, идемпотентности, transactional outbox, повторной доставки Kafka, GraphQL-схемы и реальных gRPC-вызовов с retry.

После запуска Docker-стенда полный пользовательский сценарий проверяется отдельной командой:

```powershell
.\scripts\e2e.ps1
```

Сценарий проверяет все роли, административные справочники, запрет доступа к чужой заявке, полный ремонт, расчет стоимости, резервирование детали, идемпотентный платеж, уведомления, аналитику и завершение Redis-сессии.

## Запуск

```powershell
docker compose up --build
```

После запуска:

- GraphQL: `http://localhost:8080/graphql`
- GraphiQL: `http://localhost:8080/graphiql`
- Health Gateway: `http://localhost:8080/actuator/health`
- REST-совместимость входа: `http://localhost:8081/api/auth/login`

Compose не удаляет volume PostgreSQL. Для повторного запуска на полностью чистых базах volume нужно удалить вручную.

## Тестовые пользователи

| Роль | Логин | Пароль |
|---|---|---|
| Клиент | `client@example.com` | `Client123!` |
| Сотрудник | `employee@example.com` | `Employee123!` |
| Администратор | `admin@example.com` | `Admin123!` |

## Аутентификация

Получение JWT:

```graphql
mutation Login {
  login(input: { email: "client@example.com", password: "Client123!" }) {
    accessToken
    expiresAt
    user { id email role }
  }
}
```

Во всех защищенных операциях передается HTTP-заголовок:

```text
Authorization: Bearer <accessToken>
```

Gateway проверяет подпись и срок JWT, затем подтверждает активность сессии через Identity Service и Redis. `logout` удаляет Redis-сессию.

## Полный сценарий ремонта

Идентификатор категории стиральной машины из начального справочника: `40000000-0000-0000-0000-000000000001`.

1. Клиент добавляет устройство:

```graphql
mutation AddDevice {
  upsertDevice(input: {
    categoryId: "40000000-0000-0000-0000-000000000001"
    brand: "Bosch"
    model: "WAN24260"
    serialNumber: "BOSCH-DEMO-001"
  }) { id brand model categoryName }
}
```

2. Клиент создает заявку, используя полученный `deviceId`:

```graphql
mutation CreateRequest {
  createRepairRequest(input: {
    deviceId: "<deviceId>"
    problemDescription: "Стиральная машина не сливает воду"
    priority: 3
  }) { id status priority }
}
```

3. Сотрудник переводит заявку в диагностику и создает заказ-наряд:

```graphql
mutation StartDiagnostics {
  changeRepairRequestStatus(input: { requestId: "<requestId>", status: DIAGNOSING }) { id status }
}

mutation Diagnose {
  diagnoseAndCreateWorkOrder(input: {
    requestId: "<requestId>"
    diagnosis: "Неисправен сливной насос"
  }) { id status totalAmount }
}
```

4. Сотрудник добавляет услугу и резервирует деталь. Начальные UUID: услуга диагностики `60000000-0000-0000-0000-000000000001`, сливной насос `70000000-0000-0000-0000-000000000001`.

```graphql
mutation AddService {
  addServiceToWorkOrder(input: {
    workOrderId: "<workOrderId>"
    serviceId: "60000000-0000-0000-0000-000000000001"
    quantity: 1
  }) { id totalAmount services { name quantity lineTotal } }
}

mutation AddPart {
  addPartToWorkOrder(input: {
    workOrderId: "<workOrderId>"
    partId: "70000000-0000-0000-0000-000000000001"
    quantity: 1
    idempotencyKey: "demo-part-reservation-1"
  }) { id totalAmount parts { name quantity lineTotal } }
}
```

5. Сотрудник ведет статусы `DIAGNOSED -> IN_PROGRESS -> READY -> COMPLETED`. Заявка ведется по цепочке `NEW -> DIAGNOSING -> IN_PROGRESS -> READY -> COMPLETED`.

```graphql
mutation Ready {
  changeWorkOrderStatus(input: { workOrderId: "<workOrderId>", status: READY }) { id status totalAmount }
}
```

6. Клиент регистрирует платеж. Повтор команды с тем же `idempotencyKey` возвращает ранее созданный платеж:

```graphql
mutation Pay {
  registerPayment(input: {
    workOrderId: "<workOrderId>"
    method: "CARD"
    idempotencyKey: "demo-payment-1"
  }) { id amount status providerPaymentId }
}
```

7. Клиент получает уведомления:

```graphql
query Notifications {
  notifications { id type message read createdAt }
}
```

8. Администратор получает аналитику:

```graphql
query Analytics {
  repairAnalytics(period: { from: "2026-01-01", to: "2026-12-31" }) {
    repairsCount
    completedCount
    revenue
    avgDurationHours
    mostRequestedService
    popularServices { name usageCount revenue }
    employeeWorkload { employeeId assignedCount completedCount }
  }
}
```

## Надежность

- Все изменяющие операции сначала сохраняют данные и событие в одной транзакции PostgreSQL через transactional outbox.
- Публикация Kafka ожидает подтверждение брокера. Consumer выполняет два повтора и отправляет необработанное сообщение в `<topic>.DLT`.
- Notification Service, Analytics Service и проекция стоимости заявки сохраняют `eventId` в PostgreSQL и безопасно обрабатывают повторную доставку после перезапуска.
- gRPC-клиенты используют deadline 2 секунды, до трех попыток только для временных ошибок и передают correlation ID.
- Каталог кешируется в Redis на 5 минут и инвалидируется после изменения услуги, детали или остатка.
- Резервирование деталей выполняется атомарным SQL-обновлением и защищено `idempotencyKey`.
- Клиентские выборки и команды проверяют владельца устройства, заявки, заказ-наряда, платежа и уведомления.
