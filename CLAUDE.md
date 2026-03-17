# АОС ПИ — Автоматизированная обучающая система подготовки специалистов по пневмоиспытаниям ДУ РКН

Система предназначена для подготовки и аттестации специалистов группы обеспечения пневмоиспытаний двигательной установки космического ракетного комплекса «Байтерек» (АО «СП «Байтерек», Байконур). Реализует изучение теории, конструктор тестов, прохождение экзаменов с таймером, формирование протоколов и мнемосхему пневмосистемы.

> **Мелкие правки / баг-фикс:** читай `CODESTYLE.md`
> **Новая сущность с нуля:** читай `CODESTYLE.md` + `TEMPLATES.md`
> **Планирование / статус этапов:** читай `PLAN.md`

---

## Стек технологий

- **Java 21**, **Spring Boot** (последняя стабильная)
- **Spring Security 6** — BCrypt, роли через GrantedAuthority
- **Spring Data JPA** / Hibernate — MySQL
- **Thymeleaf** — серверный рендеринг + Bootstrap 5 (без jQuery)
- **MapStruct** — маппинг Entity ↔ DTO (без Spring componentModel)
- **Lombok** — `@Data`, `@Slf4j`, `@NoArgsConstructor`, `@EqualsAndHashCode`, `@Getter`
- **Apache POI** — Excel-отчёты
- **Maven** (Maven Wrapper)

```bash
mvnw.cmd clean package
mvnw.cmd spring-boot:run
```

`application.properties` — в `.gitignore`, настройки БД задаются локально.

---

## Структура проекта

```
src/main/java/com/mai/aospi/
├── AospiApplication.java
├── component/                    — DataInitializer (CommandLineRunner)
├── controllers/
│   ├── general/                  — MainController, CustomErrorController
│   └── employee/
│       ├── admin/                — ROLE_EMPLOYEE_ADMIN
│       ├── chief/                — ROLE_EMPLOYEE_CHIEF
│       ├── specialist/           — ROLE_EMPLOYEE_SPECIALIST + OPERATOR
│       └── general/              — общие для нескольких ролей
├── dto/                          — все DTO (плоский пакет, без подпакетов)
├── enumeration/                  — все enum (плоский пакет)
├── mapper/                       — все MapStruct-маперы (плоский пакет)
├── models/                       — все JPA-сущности (плоский пакет)
├── repo/                         — все репозитории (плоский пакет)
├── security/                     — SecurityConfig, CustomUserDetailsService
└── service/
    ├── employee/admin/, chief/, specialist/
    └── general/

src/main/resources/
├── logback.xml
├── static/css/, static/js/
└── templates/
    ├── blocks/header.html, footer.html
    ├── general/                  — login, index, error
    └── employee/
        ├── admin/users/
        ├── chief/tests/, chief/materials/, chief/results/, chief/analytics/
        └── specialist/theory/, specialist/testing/, specialist/results/, specialist/mnemo/
```

---

## Роли и доступ

| Роль в ТЗ | Spring Security | URL-префикс | Доступ |
|---|---|---|---|
| Администратор | `ROLE_EMPLOYEE_ADMIN` | `/employee/admin/` | Управление пользователями |
| Начальник группы | `ROLE_EMPLOYEE_CHIEF` | `/employee/chief/` | Тесты, материалы, протоколы, аналитика |
| Специалист | `ROLE_EMPLOYEE_SPECIALIST` | `/employee/specialist/` | Теория, тестирование, результаты |
| Оператор | `ROLE_EMPLOYEE_OPERATOR` | `/employee/specialist/` | Те же права что у специалиста |

`Employee` реализует `UserDetails`. Начальный admin создаётся при старте через `DataInitializer`. Деактивированный пользователь (`isActive=false`) не может войти.

---

## Доменная модель

| Блок | Сущности |
|---|---|
| Пользователи | `Employee`, `EmployeeRole` (enum) |
| Теория | `TheorySection`, `TheoryMaterial`, `MaterialType` (enum) |
| Тесты | `Test`, `TestQuestion`, `TestAnswer`, `QuestionType` (enum) |
| Результаты | `TestSession`, `TestSessionAnswer`, `TestSessionStatus` (enum) |
| Мнемосхема *(этап 7)* | `SimulationScenario`, `SimulationState`, `SimulationElement` |

### Ключевые поля сущностей

**Employee** `t_employee` — lastName, firstName, middleName, birthDate, subdivision, position, username *(unique)*, password *(BCrypt)*, role *(EmployeeRole)*, isActive

**EmployeeRole** — `ADMIN("Администратор")`, `CHIEF("Начальник группы")`, `SPECIALIST("Специалист")`, `OPERATOR("Оператор")`

**TheorySection** `t_theorySection` — title, sortOrder, description → OneToMany TheoryMaterial

**TheoryMaterial** `t_theoryMaterial` — title, content *(TEXT)*, sortOrder, materialType *(MaterialType)* → ManyToOne TheorySection

**MaterialType** — `TEXT("Текст")`, `PDF("PDF-документ")`, `VIDEO_LINK("Видео")`

**Test** `t_test` — title, description, timeLimit *(минуты)*, passingScore *(%)*, isExam, isActive → ManyToOne Employee (createdBy) → OneToMany TestQuestion

**TestQuestion** `t_testQuestion` — questionText, sortOrder, questionType *(QuestionType)* → ManyToOne Test → OneToMany TestAnswer

**QuestionType** — `SINGLE_CHOICE("Один ответ")`, `MULTIPLE_CHOICE("Несколько ответов")`, `SEQUENCE("Очерёдность")`, `MATCHING("Соответствие")`, `OPEN_TEXT("Открытый ответ")`

**TestAnswer** `t_testAnswer` — answerText, isCorrect, sortOrder, matchTarget → ManyToOne TestQuestion

**TestSession** `t_testSession` — startedAt, finishedAt, score, totalScore, scorePercent, isPassed, sessionStatus → ManyToOne Employee, ManyToOne Test → OneToMany TestSessionAnswer

**TestSessionStatus** — `IN_PROGRESS("В процессе")`, `COMPLETED("Завершён")`, `EXPIRED("Время истекло")`, `INTERRUPTED("Прерван")`

**TestSessionAnswer** `t_testSessionAnswer` — answerText, isCorrect → ManyToOne TestSession, ManyToOne TestQuestion, ManyToMany TestAnswer

---

## URL-карта проекта

```
# Admin
GET/POST /employee/admin/users/allUsers
GET/POST /employee/admin/users/addUser
GET      /employee/admin/users/detailsUser/{id}
GET/POST /employee/admin/users/editUser/{id}
GET      /employee/admin/users/deactivateUser/{id}
GET/POST /employee/admin/users/resetPassword/{id}
GET      /employee/admin/users/check-username

# Chief — материалы
GET/POST /employee/chief/materials/allSections
GET/POST /employee/chief/materials/addSection
GET/POST /employee/chief/materials/editSection/{id}
GET      /employee/chief/materials/detailsSection/{id}
GET      /employee/chief/materials/deleteSection/{id}
GET/POST /employee/chief/materials/addMaterial/{sectionId}
GET/POST /employee/chief/materials/editMaterial/{id}
GET      /employee/chief/materials/deleteMaterial/{id}

# Chief — тесты
GET/POST /employee/chief/tests/allTests
GET/POST /employee/chief/tests/addTest
GET/POST /employee/chief/tests/editTest/{id}
GET      /employee/chief/tests/detailsTest/{id}
GET      /employee/chief/tests/deleteTest/{id}
GET      /employee/chief/tests/activateTest/{id}
GET/POST /employee/chief/tests/addQuestion/{testId}
GET/POST /employee/chief/tests/editQuestion/{questionId}
GET      /employee/chief/tests/deleteQuestion/{questionId}

# Chief — результаты и аналитика
GET      /employee/chief/results/allResults
GET      /employee/chief/results/detailsResult/{sessionId}
GET      /employee/chief/results/journal
POST     /employee/chief/results/exportExamProtocol
POST     /employee/chief/results/exportJournal
GET      /employee/chief/analytics/dashboard

# Specialist — теория
GET      /employee/specialist/theory/allSections
GET      /employee/specialist/theory/viewSection/{id}
GET      /employee/specialist/theory/viewMaterial/{id}

# Specialist — тестирование
GET      /employee/specialist/testing/availableTests
GET/POST /employee/specialist/testing/startTest/{testId}
GET      /employee/specialist/testing/question/{sessionId}/{questionIndex}
POST     /employee/specialist/testing/submitAnswer/{sessionId}/{questionIndex}
GET      /employee/specialist/testing/finishTest/{sessionId}
GET      /employee/specialist/testing/result/{sessionId}

# Specialist — результаты
GET      /employee/specialist/results/myResults
GET      /employee/specialist/results/detailsResult/{sessionId}
GET      /employee/specialist/results/exportResult/{sessionId}

# Мнемосхема (этап 7 — ожидание материалов от заказчика)
GET      /employee/specialist/mnemo/scenarios
GET/POST /employee/specialist/mnemo/startScenario/{scenarioId}
GET      /employee/specialist/mnemo/view/{sessionId}
POST     /employee/specialist/mnemo/toggleElement/{sessionId}
GET      /employee/specialist/mnemo/getState/{sessionId}
GET      /employee/specialist/mnemo/result/{sessionId}
```

---

## Специфика проекта

### Таймер тестирования
- `TestSession.endTime` = `startedAt + timeLimit (минуты)`
- Таймер на клиенте (vanilla JS countdown), значение `endTime` передаётся через `data-end-time`
- При 0 → автосабмит формы → сервер ставит `EXPIRED`
- При каждом запросе сервер проверяет `now() > session.endTime`

### Безопасность тестовых сессий
- При каждом GET/POST `/testing/...` проверять: `session.employee.id == currentUser.id`
- Если нет → `redirect:/employee/specialist/testing/availableTests`

### Деактивация вместо удаления пользователей
- Не удалять, только `isActive = false`
- Кнопка: «Деактивировать», не «Удалить»

### Типы вопросов — отображение
- `SINGLE_CHOICE` → radio buttons
- `MULTIPLE_CHOICE` → checkboxes
- `SEQUENCE` → drag-and-drop список (vanilla JS)
- `MATCHING` → два столбца, select или drag-and-drop
- `OPEN_TEXT` → textarea (ручная проверка начальником)

### Excel-отчёты (Apache POI)
- **Отчёт самотестирования** — шапка (ФИО, дата, тест) + таблица вопросов с ответами
- **Протокол экзамена** — шапка протокола + матрица (ФИО × балл × % × пройден)
- **Электронный журнал** — матрица (сотрудник × тест × последний результат %)

### Глоссарий
| Термин | Значение |
|---|---|
| ДУ | Двигательная установка |
| РКН | Ракета-носитель космическая |
| КРК «Байтерек» | Космический ракетный комплекс «Байтерек» |
| ПИ | Пневмоиспытания |
| Мнемосхема | Интерактивная схема трубопроводов пневмосистемы |
| VP | Пневмоклапан (VP1–VP48) |
| NR | Насос-регулятор давления (NR1–NR8) |
| PT | Датчик давления (PT1–PT12) |
| NK | Блок конденсации (NK1–NK3) |

---

## Git

- **Никогда** не работай в `main`/`master`/`develop`
- Ветка: `git checkout -b claude/{описание}`
- Коммиты: `feat: ...` / `fix: ...`
- Каждый запрос от пользователя с изменением кода делается в новом PR
- **Не пушить** без явного разрешения пользователя