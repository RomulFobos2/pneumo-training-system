# AGENTS.md

This file defines repository-level instructions for Codex and applies to the entire tree rooted at this directory.

## Project Overview

АОС ПИ — автоматизированная обучающая система подготовки специалистов по пневмоиспытаниям ДУ РКН.

Система предназначена для подготовки и аттестации специалистов группы обеспечения пневмоиспытаний двигательной установки космического ракетного комплекса «Байтерек» (АО «СП "Байтерек"», Байконур). Реализует изучение теории, конструктор тестов, прохождение экзаменов с таймером, формирование протоколов и мнемосхему пневмосистемы.

## Instruction Routing

- For small fixes and bugfixes, read `CODESTYLE.md`.
- For implementing a new entity or feature from scratch, read `CODESTYLE.md` and `TEMPLATES.md`.
- For planning work and tracking stages, read `PLAN.md`.

## Tech Stack

- Java 21
- Spring Boot
- Spring Security 6 with BCrypt and roles via `GrantedAuthority`
- Spring Data JPA / Hibernate with MySQL
- Thymeleaf with server-side rendering and Bootstrap 5, without jQuery
- MapStruct for `Entity <-> DTO` mapping, without Spring `componentModel`
- Lombok with `@Data`, `@Slf4j`, `@NoArgsConstructor`, `@EqualsAndHashCode`, `@Getter`
- Apache POI for Excel reports
- Maven Wrapper

Primary commands:

```bash
mvnw.cmd clean package
mvnw.cmd spring-boot:run
```

`application.properties` is ignored by git; database settings are configured locally.

## Project Structure

```text
src/main/java/com/mai/aospi/
├── AospiApplication.java
├── component/                    — DataInitializer (CommandLineRunner)
├── controllers/
│   ├── general/                  — MainController, CustomErrorController
│   └── employee/
│       ├── admin/                — ROLE_EMPLOYEE_ADMIN
│       ├── chief/                — ROLE_EMPLOYEE_CHIEF
│       ├── specialist/           — ROLE_EMPLOYEE_SPECIALIST + OPERATOR
│       └── general/              — shared for multiple roles
├── dto/                          — all DTOs, flat package without subpackages
├── enumeration/                  — all enums, flat package
├── mapper/                       — all MapStruct mappers, flat package
├── models/                       — all JPA entities, flat package
├── repo/                         — all repositories, flat package
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

## Roles And Access

| Business role | Spring Security role | URL prefix | Access |
|---|---|---|---|
| Администратор | `ROLE_EMPLOYEE_ADMIN` | `/employee/admin/` | User management |
| Начальник группы | `ROLE_EMPLOYEE_CHIEF` | `/employee/chief/` | Tests, materials, protocols, analytics |
| Специалист | `ROLE_EMPLOYEE_SPECIALIST` | `/employee/specialist/` | Theory, testing, results |
| Оператор | `ROLE_EMPLOYEE_OPERATOR` | `/employee/specialist/` | Same permissions as specialist |

- `Employee` implements `UserDetails`.
- Initial admin is created at startup via `DataInitializer`.
- Deactivated users with `isActive=false` must not be able to log in.

## Domain Model

| Block | Entities |
|---|---|
| Users | `Employee`, `EmployeeRole` |
| Theory | `TheorySection`, `TheoryMaterial`, `MaterialType` |
| Tests | `Test`, `TestQuestion`, `TestAnswer`, `QuestionType` |
| Results | `TestSession`, `TestSessionAnswer`, `TestSessionStatus` |
| Mnemonic scheme, stage 7 | `SimulationScenario`, `SimulationState`, `SimulationElement` |

Key fields:

- `Employee` / `t_employee`: `lastName`, `firstName`, `middleName`, `birthDate`, `subdivision`, `position`, `username` unique, `password` BCrypt, `role`, `isActive`
- `EmployeeRole`: `ADMIN`, `CHIEF`, `SPECIALIST`, `OPERATOR`
- `TheorySection` / `t_theorySection`: `title`, `sortOrder`, `description`
- `TheoryMaterial` / `t_theoryMaterial`: `title`, `content`, `sortOrder`, `materialType`
- `MaterialType`: `TEXT`, `PDF`, `VIDEO_LINK`
- `Test` / `t_test`: `title`, `description`, `timeLimit`, `passingScore`, `isExam`, `isActive`, `createdBy`
- `TestQuestion` / `t_testQuestion`: `questionText`, `sortOrder`, `questionType`
- `QuestionType`: `SINGLE_CHOICE`, `MULTIPLE_CHOICE`, `SEQUENCE`, `MATCHING`, `OPEN_TEXT`
- `TestAnswer` / `t_testAnswer`: `answerText`, `isCorrect`, `sortOrder`, `matchTarget`
- `TestSession` / `t_testSession`: `startedAt`, `finishedAt`, `score`, `totalScore`, `scorePercent`, `isPassed`, `sessionStatus`
- `TestSessionStatus`: `IN_PROGRESS`, `COMPLETED`, `EXPIRED`, `INTERRUPTED`
- `TestSessionAnswer` / `t_testSessionAnswer`: `answerText`, `isCorrect`

## URL Map

```text
# Admin
GET/POST /employee/admin/users/allUsers
GET/POST /employee/admin/users/addUser
GET      /employee/admin/users/detailsUser/{id}
GET/POST /employee/admin/users/editUser/{id}
GET      /employee/admin/users/deactivateUser/{id}
GET/POST /employee/admin/users/resetPassword/{id}
GET      /employee/admin/users/check-username

# Chief — materials
GET/POST /employee/chief/materials/allSections
GET/POST /employee/chief/materials/addSection
GET/POST /employee/chief/materials/editSection/{id}
GET      /employee/chief/materials/detailsSection/{id}
GET      /employee/chief/materials/deleteSection/{id}
GET/POST /employee/chief/materials/addMaterial/{sectionId}
GET/POST /employee/chief/materials/editMaterial/{id}
GET      /employee/chief/materials/deleteMaterial/{id}

# Chief — tests
GET/POST /employee/chief/tests/allTests
GET/POST /employee/chief/tests/addTest
GET/POST /employee/chief/tests/editTest/{id}
GET      /employee/chief/tests/detailsTest/{id}
GET      /employee/chief/tests/deleteTest/{id}
GET      /employee/chief/tests/activateTest/{id}
GET/POST /employee/chief/tests/addQuestion/{testId}
GET/POST /employee/chief/tests/editQuestion/{questionId}
GET      /employee/chief/tests/deleteQuestion/{questionId}

# Chief — results and analytics
GET      /employee/chief/results/allResults
GET      /employee/chief/results/detailsResult/{sessionId}
GET      /employee/chief/results/journal
POST     /employee/chief/results/exportExamProtocol
POST     /employee/chief/results/exportJournal
GET      /employee/chief/analytics/dashboard

# Specialist — theory
GET      /employee/specialist/theory/allSections
GET      /employee/specialist/theory/viewSection/{id}
GET      /employee/specialist/theory/viewMaterial/{id}

# Specialist — testing
GET      /employee/specialist/testing/availableTests
GET/POST /employee/specialist/testing/startTest/{testId}
GET      /employee/specialist/testing/question/{sessionId}/{questionIndex}
POST     /employee/specialist/testing/submitAnswer/{sessionId}/{questionIndex}
GET      /employee/specialist/testing/finishTest/{sessionId}
GET      /employee/specialist/testing/result/{sessionId}

# Specialist — results
GET      /employee/specialist/results/myResults
GET      /employee/specialist/results/detailsResult/{sessionId}
GET      /employee/specialist/results/exportResult/{sessionId}

# Mnemonic scheme, stage 7
GET      /employee/specialist/mnemo/scenarios
GET/POST /employee/specialist/mnemo/startScenario/{scenarioId}
GET      /employee/specialist/mnemo/view/{sessionId}
POST     /employee/specialist/mnemo/toggleElement/{sessionId}
GET      /employee/specialist/mnemo/getState/{sessionId}
GET      /employee/specialist/mnemo/result/{sessionId}
```

## Project-Specific Invariants

### Test Timer

- `TestSession.endTime` must equal `startedAt + timeLimit` in minutes.
- The client timer uses vanilla JS countdown and receives `endTime` via `data-end-time`.
- When the timer reaches zero, the form must auto-submit and the server must set status `EXPIRED`.
- On each relevant request, the server must verify `now() > session.endTime`.

### Test Session Security

- On each GET or POST under `/testing/...`, verify `session.employee.id == currentUser.id`.
- If the session does not belong to the current user, redirect to `/employee/specialist/testing/availableTests`.

### User Deactivation

- Never hard-delete users as part of normal application behavior.
- Deactivate users via `isActive = false`.
- UI wording should use "Деактивировать", not "Удалить".

### Question Type Rendering

- `SINGLE_CHOICE` -> radio buttons
- `MULTIPLE_CHOICE` -> checkboxes
- `SEQUENCE` -> drag-and-drop list in vanilla JS
- `MATCHING` -> two columns, select or drag-and-drop
- `OPEN_TEXT` -> textarea with manual review by chief

### Excel Reports

- Self-test report: header with full name, date, and test plus table of questions and answers
- Exam protocol: protocol header plus matrix with full name, score, percent, and pass status
- Electronic journal: matrix with employee, test, and latest percent result

## Glossary

| Term | Meaning |
|---|---|
| ДУ | Двигательная установка |
| РКН | Ракета-носитель космическая |
| КРК «Байтерек» | Космический ракетный комплекс «Байтерек» |
| ПИ | Пневмоиспытания |
| Мнемосхема | Интерактивная схема трубопроводов пневмосистемы |
| VP | Пневмоклапан |
| NR | Насос-регулятор давления |
| PT | Датчик давления |
| NK | Блок конденсации |

## Git Workflow

- Never work directly in `main`, `master`, or `develop`.
- Use a separate task branch for each code change request.
- In Codex, prefer branch names with the `codex/` prefix, preserving the intent of the existing `claude/{description}` rule from `CLAUDE.md`.
- Use conventional commit prefixes such as `feat:` and `fix:`.
- Do not push without explicit user approval.
- If the user asks for code changes, assume they should be isolated in a new PR or equivalent branch-scoped unit of work.
