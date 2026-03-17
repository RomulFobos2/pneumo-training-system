# PLAN.md — План разработки АОС ПИ

> Читай вместе с `CLAUDE.md`. При написании кода дополнительно читай `CODESTYLE.md`.

---

## Статус этапов

| Этап | Название | Статус |
|---|---|---|
| 0 | Фундамент (Security, Employee, UI-блоки) | 🔲 Не начат |
| 1 | Управление пользователями (Admin) | 🔲 Не начат |
| 2 | Учебные материалы (Chief + Specialist) | 🔲 Не начат |
| 3 | Конструктор тестов (Chief) | 🔲 Не начат |
| 4 | Прохождение тестирования (Specialist) | 🔲 Не начат |
| 5 | Результаты и Excel-отчёты | 🔲 Не начат |
| 6 | Аналитический дашборд (Chief) | 🔲 Не начат |
| 7 | Мнемосхема + симуляция | ⏸ Ожидание материалов |

---

## ЭТАП 0: Фундамент

**Цель:** скелет приложения — безопасность, начальные данные, UI-блоки.

### 0.1 Инициализация Maven-проекта
- [ ] Spring Initializr: Java 21, Spring Boot, Security, JPA, Thymeleaf, MySQL, Lombok, MapStruct, POI
- [ ] Настроить `application.properties`: datasource, JPA DDL, Thymeleaf
- [ ] Создать структуру пакетов (см. CLAUDE.md → Структура проекта)

### 0.2 Сущность Employee + Security
Файлы:
- `models/Employee.java` — lastName, firstName, middleName, birthDate, subdivision, position, username, password, role (EmployeeRole), isActive
- `enumeration/EmployeeRole.java` — ADMIN, CHIEF, SPECIALIST, OPERATOR с displayName
- `repo/EmployeeRepository.java` — existsByUsername, findByUsername
- `dto/EmployeeDTO.java`
- `mapper/EmployeeMapper.java`
- `security/SecurityConfig.java` — настройка URL и ролей
- `security/CustomUserDetailsService.java` — реализует UserDetailsService, проверяет isActive
- `component/DataInitializer.java` — создаёт роли и admin при первом старте

### 0.3 UI-блоки
- [ ] `templates/blocks/header.html` — навигация с `th:if` по ролям
- [ ] `templates/blocks/footer.html`
- [ ] `static/css/style.css` — полный файл стилей (profile-card, table-modern, auth-form, page-title и т.д.)
- [ ] `templates/general/login.html`
- [ ] `templates/general/index.html` — dashboard с блоками по роли
- [ ] `templates/general/error.html`
- [ ] `controllers/general/MainController.java` — `/` → redirect по роли, `/login`

### Проверка этапа 0
- [ ] Логин/логаут работают
- [ ] Каждая роль видит только свои разделы в меню
- [ ] 403 при попытке зайти не в свой раздел
- [ ] Admin создаётся при первом запуске

---

## ЭТАП 1: Управление пользователями (Admin)

**Цель:** администратор управляет учётными записями.

### Особенности
- Удаление = деактивация (`isActive = false`), кнопка «Деактивировать»
- Сброс пароля — отдельная операция на отдельной странице
- AJAX-проверка уникальности username

### Файлы
```
service/employee/admin/UserService.java
controllers/employee/admin/UserController.java
templates/employee/admin/users/
    allUsers.html       — таблица + фильтр по роли
    addUser.html        — ФИО, дата рождения, подразделение, должность, роль, логин
    editUser.html       — без смены пароля
    detailsUser.html    — карточка + кнопки Редактировать / Деактивировать / Сбросить пароль
    resetPassword.html  — форма сброса пароля
```

### Методы UserService
- `checkUsername(username, id)` — уникальность логина
- `saveUser(user, rawPassword)` — BCrypt при создании
- `editUser(id, ...)` — данные без пароля
- `resetPassword(id, newRawPassword)` — отдельная операция
- `deactivateUser(id)` — `isActive = false`
- `getAllUsers()`, `getAllByRole(role)`

---

## ЭТАП 2: Учебные материалы

**Цель:** chief управляет теорией, specialist читает.

### Файлы
```
models/TheorySection.java
models/TheoryMaterial.java
enumeration/MaterialType.java

service/employee/chief/TheorySectionService.java
service/employee/chief/TheoryMaterialService.java
controllers/employee/chief/TheorySectionController.java
controllers/employee/chief/TheoryMaterialController.java
templates/employee/chief/materials/
    allSections.html
    addSection.html, editSection.html, detailsSection.html
    addMaterial.html, editMaterial.html, detailsMaterial.html

service/employee/specialist/TheoryViewService.java     (только read)
controllers/employee/specialist/TheoryViewController.java
templates/employee/specialist/theory/
    allSections.html    — список разделов с кол-вом материалов
    viewSection.html    — список материалов раздела
    viewMaterial.html   — просмотр: TEXT inline / PDF iframe / VIDEO iframe
```

### Особенности
- TEXT: `<textarea>` для ввода HTML-контента, отображение через `th:utext`
- PDF: поле URL, отображение `<iframe>`
- VIDEO_LINK: поле URL YouTube, embed через `<iframe>`
- Сортировка по `sortOrder`

---

## ЭТАП 3: Конструктор тестов (Chief)

**Цель:** создание тестов с 5 типами вопросов.

### Файлы
```
models/Test.java
models/TestQuestion.java
models/TestAnswer.java
enumeration/QuestionType.java

service/employee/chief/TestService.java
service/employee/chief/TestQuestionService.java
controllers/employee/chief/TestController.java
controllers/employee/chief/TestQuestionController.java
templates/employee/chief/tests/
    allTests.html           — список тестов
    addTest.html            — название, описание, таймер, проходной балл, флаг экзамена
    editTest.html
    detailsTest.html        — ← главный экран: список вопросов + кнопки управления
    addQuestion.html        — выбор типа → динамическая форма ответов (JS)
    editQuestion.html
```

### Особенности addQuestion
- `<select>` типа вопроса → JS показывает нужный блок
- Динамическое добавление вариантов ответа: кнопка «+ Добавить вариант» (без AJAX, DOM)
- SINGLE/MULTIPLE: поля ответ + чекбокс isCorrect
- SEQUENCE: поля ответ + номер порядка
- MATCHING: два столбца inputLeft / inputRight
- OPEN_TEXT: одно поле правильного ответа

---

## ЭТАП 4: Прохождение тестирования (Specialist)

**Цель:** специалист проходит тест с таймером, получает результат.

### Файлы
```
models/TestSession.java
models/TestSessionAnswer.java
enumeration/TestSessionStatus.java

service/employee/specialist/TestingService.java
controllers/employee/specialist/TestingController.java
templates/employee/specialist/testing/
    availableTests.html     — активные тесты, ещё не начатые
    startTest.html          — подтверждение: условия, правила, кнопка «Начать»
    question.html           — один вопрос + таймер + прогресс-бар
    result.html             — итог: баллы, % , пройден/нет, разбор ошибок
```

### Логика прохождения
```
POST startTest/{testId}
  → создать TestSession (status=IN_PROGRESS, endTime = now + timeLimit)
  → перемешать вопросы
  → redirect на question/{sessionId}/0

GET question/{sessionId}/{questionIndex}
  → проверить: сессия принадлежит currentUser && status == IN_PROGRESS && now < endTime
  → если time > endTime → finishTest с EXPIRED
  → показать вопрос №questionIndex

POST submitAnswer/{sessionId}/{questionIndex}
  → сохранить TestSessionAnswer
  → если questionIndex == lastIndex → redirect finishTest
  → иначе → redirect question/{sessionId}/{questionIndex+1}

GET finishTest/{sessionId}
  → подсчитать score / totalScore / scorePercent / isPassed
  → status = COMPLETED
  → redirect result/{sessionId}
```

### Таймер (vanilla JS)
```javascript
const endTime = new Date(/*[[${session.endTime}]]*/);
function tick() {
    const remaining = endTime - new Date();
    if (remaining <= 0) {
        document.getElementById('autoSubmitForm').submit();
        return;
    }
    const m = Math.floor(remaining / 60000);
    const s = Math.floor((remaining % 60000) / 1000);
    document.getElementById('timer').textContent =
        String(m).padStart(2,'0') + ':' + String(s).padStart(2,'0');
}
setInterval(tick, 1000);
tick();
```

---

## ЭТАП 5: Результаты и отчёты

**Цель:** specialist — свои результаты; chief — все результаты + Excel.

### Файлы
```
service/employee/specialist/ResultService.java
controllers/employee/specialist/ResultController.java
templates/employee/specialist/results/
    myResults.html          — список своих сессий
    detailsResult.html      — детали: вопросы + ответы пользователя + правильные ответы

service/employee/chief/ReportService.java      (Apache POI)
controllers/employee/chief/ReportController.java
templates/employee/chief/results/
    allResults.html         — все сессии + фильтры (дата, тест, пользователь)
    detailsResult.html
    journal.html            — сводная таблица: сотрудник × тест
    reportParams.html       — форма параметров для Excel
```

### Excel-структура
- **Отчёт самотестирования**: шапка (ФИО, дата, тест, результат) + таблица вопросов
- **Протокол экзамена**: шапка протокола + таблица (ФИО | дата | балл | % | пройден)
- **Журнал**: матрица (строка = сотрудник, столбцы = тесты, значения = последний %)

---

## ЭТАП 6: Аналитика (Chief)

**Цель:** дашборд с агрегированной статистикой.

### Файлы
```
service/employee/chief/AnalyticsService.java
controllers/employee/chief/AnalyticsController.java
templates/employee/chief/analytics/dashboard.html
```

### Метрики
- Кол-во сотрудников (активных / всего)
- Тесты за 30 дней (пройдено / назначено)
- Таблица: тест → % прохождения
- Топ-5 сотрудников по среднему баллу
- Топ-5 сложных вопросов (% неправильных)
- График активности по дням (`<canvas>` + vanilla JS CanvasRenderingContext2D)

---

## ЭТАП 7: Мнемосхема и симуляция ⏸

> **Ожидание материалов от заказчика.** Не начинать до получения всего списка ниже.

### Что нужно получить от заказчика
- [ ] Полная схема пневмосистемы ДУ РКН (чертёж или схема)
- [ ] Перечень всех элементов: клапаны VP1–VP48, насосы NR1–NR8, датчики PT1–PT12, блоки NK1–NK3
- [ ] Таблица допустимых состояний (конфигурация клапанов → показания датчиков)
- [ ] Перечень учебных сценариев (нормальное прохождение + внештатные ситуации)
- [ ] SVG-схема или чертёж для отрисовки мнемосхемы

### Технический подход (после получения материалов)
- SVG встраивается в Thymeleaf-страницу
- Каждый клапан: `id="vp-1"` в SVG
- Клик → `fetch POST /mnemo/toggleElement/{sessionId}` → JSON с новым состоянием
- JS обновляет SVG: цвет трубопровода, значения датчиков, иконки
- Состояние симуляции — в `HttpSession` (не в БД, сессия эфемерна)

### Файлы (отложено)
```
models/SimulationScenario.java
models/SimulationState.java
models/SimulationElement.java

service/employee/specialist/MnemoService.java
service/employee/specialist/SimulationEngine.java
controllers/employee/specialist/MnemoController.java
templates/employee/specialist/mnemo/
    scenarios.html
    view.html          — SVG-мнемосхема + JS-интерактив
    result.html
static/svg/pneumo-scheme.svg
```
