# PLAN.md — План разработки АОС ПИ

> Читай вместе с `CLAUDE.md`. При написании кода дополнительно читай `CODESTYLE.md`.

---

## Статус этапов

| Этап | Название | Статус |
|---|---|---|
| 0 | Фундамент (Security, Employee, UI-блоки) | ✅ Выполнен |
| 1 | Управление пользователями (Admin) | ✅ Выполнен |
| 2 | Учебные материалы (Chief + Specialist) | 🔲 Не начат |
| 3 | Конструктор тестов (Chief) | 🔲 Не начат |
| 4 | Прохождение тестирования (Specialist) | 🔲 Не начат |
| 5 | Результаты и Excel-отчёты | 🔲 Не начат |
| 6 | Аналитический дашборд (Chief) | 🔲 Не начат |
| 7 | Мнемосхема + симуляция | ⏸ Ожидание материалов |

---

## ЭТАП 0: Фундамент ✅

**Цель:** скелет приложения — безопасность, начальные данные, UI-блоки.

### 0.1 Инициализация Maven-проекта
- [x] Spring Initializr: Java 21, Spring Boot 3.5.11, Security, JPA, Thymeleaf, MySQL, Lombok, MapStruct, POI
- [x] Настроить `application.properties`: datasource, JPA DDL, Thymeleaf
- [x] Создать структуру пакетов (см. CLAUDE.md → Структура проекта)

### 0.2 Сущность Employee + Security
Реализовано:
- `models/Employee.java` — реализует UserDetails, поля: lastName, firstName, middleName, birthDate, subdivision, position, username, password, role, isActive, needChangePassword
- `models/Role.java` — реализует GrantedAuthority (вместо enum)
- `repo/EmployeeRepository.java`, `repo/RoleRepository.java`
- `dto/EmployeeDTO.java`, `dto/RoleDTO.java`
- `mapper/EmployeeMapper.java`, `mapper/RoleMapper.java`
- `security/SecurityConfigEmployee.java` — настройка URL и ролей
- `security/PasswordEncoderConfig.java` — BCrypt
- `security/AuthenticationLoggingSuccessHandler.java`, `AuthenticationLoggingFailureHandler.java`
- `component/DataInitializer.java` — создаёт 4 роли и admin при первом старте

### 0.3 UI-блоки
- [x] `templates/blocks/header.html` — навигация с `th:if` по ролям
- [x] `templates/blocks/footer.html`
- [x] `static/css/style.css` — полный файл стилей
- [x] `templates/employee/general/login.html`
- [x] `templates/general/home.html` — главная страница
- [x] `templates/errors/` — 403, 404, 405, error
- [x] `controllers/general/MainController.java`, `CustomErrorController.java`

### Проверка этапа 0
- [x] Логин/логаут работают
- [x] Каждая роль видит только свои разделы в меню
- [x] 403 при попытке зайти не в свой раздел
- [x] Admin создаётся при первом запуске

---

## ЭТАП 1: Управление пользователями (Admin) ✅

**Цель:** администратор управляет учётными записями.

### Особенности (реализовано)
- Удаление = деактивация (`isActive = false`), кнопка «Деактивировать»
- Сброс пароля — отдельная операция на отдельной странице
- AJAX-проверка уникальности username
- Принудительная смена пароля при первом входе (`needChangePassword`)
- Профиль пользователя и смена пароля (`profile.html`, `change-password.html`)

### Файлы (реализовано)
```
service/employee/EmployeeService.java
controllers/employee/admin/UserController.java
controllers/employee/general/MainEmployeeController.java
templates/employee/admin/users/
    allUsers.html       — таблица + фильтр по роли
    addUser.html        — ФИО, дата рождения, подразделение, должность, роль, логин
    editUser.html       — без смены пароля
    detailsUser.html    — карточка + кнопки Редактировать / Деактивировать / Сбросить пароль
    resetPassword.html  — форма сброса пароля
templates/employee/general/
    profile.html        — профиль текущего пользователя
    change-password.html — смена собственного пароля
```

### Методы EmployeeService
- `checkUserName(username)` / `checkUserNameExcluding(username, id)` — уникальность логина
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

### Справка: как эмулятор был реализован в старом дипломе (Delphi)

Старый проект содержал интерактивный тренажёр пневматической системы (Unit119.pas, ~2300 строк).
Ключевые архитектурные решения, которые стоит учесть:

**Элементы мнемосхемы:**
| Тип | Обозначения | Кол-во | Назначение |
|-----|-------------|--------|------------|
| Пневмораспределители | VP1–VP48 | 48 | Открытие/закрытие подачи воздуха |
| Клапаны | V1–V17, VФ3–VФ9 | 24 | Управление потоками |
| Насосы/нагнетатели | N1–N4 | 4 | Подача давления |
| Выключатели | WS1–WS6 | 6 | Включение/отключение узлов |
| Нагреватели/реле | NR1–NR8, NL5–NL6 | 10 | Нагревательные элементы |
| Датчики температуры | TT16–TT23, T16–T23 | 16 | Показания температуры |
| Датчики давления | P1–P18, PT1–PT13 | 31 | Показания давления |
| Блокировки | BH21, BH31, ZB1, ZB2, ZD11 | 5 | Защитные механизмы |

**Механика прохождения:**
- Пошаговый сценарий из **24 команд** (процедура запуска/останова пневмосистемы)
- На каждом шаге пользователю показывается текстовая инструкция ("Включить WS1", "Открыть VP7, VP21-VP23" и т.д.)
- Пользователь выполняет действие на мнемосхеме (кликает на элементы)
- Нажимает "Проверить" → система валидирует состояние **всех** элементов (не только текущего шага, но и предыдущих)
- Ошибка на любом шаге → аттестация провалена (строгий режим)
- **2 варианта сценария** выбираются случайно (отличаются деталями: какие именно вентили, наличие ошибки на NR1)
- Таймер: **20 минут** на прохождение
- Датчики температуры/давления обновляются динамически (таймер + случайное приращение)

**Состояния элементов:**
- Каждый элемент — бинарный: включён/открыт (Tag=1) или выключен/закрыт (Tag=0)
- Визуально: переключение между изображениями `{name}on.bmp` / `{name}off.bmp`
- Вентили управляются через отдельную форму с кнопками "Открыть"/"Закрыть"

**Оценивание:**
- Успешно пройден → `Sim_score = "Сдана"`, итоговая оценка = `ceil((Score_test + 5) / 2) + 1`
- Не пройден → `Sim_score = "Не сдана"`, итоговая оценка = 2 (неудовлетворительно)
- Итоговая оценка учитывает и тестовую часть, и результат эмулятора

**Известный баг в старом проекте:** переменная `mo` (результат) нигде не устанавливалась в 1 при успешном прохождении → эмулятор всегда возвращал "не сдана". Нужно учесть при реализации.

---

### Что нужно получить от заказчика
- [ ] Полная схема пневмосистемы ДУ РКН (чертёж или схема)
- [ ] Перечень всех элементов с указанием типов (клапаны, насосы, датчики, блокировки)
- [ ] Таблица допустимых состояний (конфигурация элементов → показания датчиков)
- [ ] Перечень учебных сценариев (пошаговые команды для нормального прохождения + внештатные ситуации)
- [ ] SVG-схема или чертёж для отрисовки мнемосхемы

### Технический подход (после получения материалов)

**Архитектура (на основе анализа старого проекта, адаптация под Spring Boot):**

**Модель данных:**
- `SimulationScenario` — сценарий прохождения (название, описание, лимит времени, isActive)
- `SimulationStep` — шаг сценария (scenarioId, stepNumber, instructionText, expectedState JSON)
- `SimulationElement` — элемент мнемосхемы (svgId, type [VALVE/PUMP/SWITCH/SENSOR/HEATER/LOCK], displayName)
- `SimulationSession` — сессия прохождения (employeeId, scenarioId, startTime, endTime, status, currentStep, score)

**Логика (аналог старого проекта, но без багов):**
1. Specialist выбирает сценарий → создаётся `SimulationSession`
2. На каждом шаге отображается инструкция (аналог `komands_message()`)
3. Пользователь кликает на элементы SVG → `fetch POST` меняет состояние в сессии
4. Кнопка "Проверить шаг" → сервер валидирует:
   - Текущий шаг: нужные элементы в правильном состоянии
   - Все предыдущие шаги: элементы не были сброшены (кумулятивная проверка, как в старом проекте)
5. Если шаг пройден → `currentStep++`, показать следующую инструкцию
6. Если ошибка → сессия завершается со статусом FAILED
7. Если все шаги пройдены → статус COMPLETED, расчёт итоговой оценки

**Датчики (динамика показаний):**
- JS-таймер обновляет значения датчиков на клиенте (как Timer3 в старом проекте)
- Значения зависят от состояния связанных элементов (открытый клапан → давление растёт)
- Формула: `newValue = currentValue + baseIncrement + random()/divisor` (из `pokazchange()`)

**SVG-мнемосхема:**
- SVG встраивается в Thymeleaf-страницу
- Каждый элемент: `id="vp-1"`, `data-type="VALVE"`, `data-state="off"`
- Клик → `fetch POST /employee/specialist/mnemo/toggleElement/{sessionId}` → JSON с новым состоянием
- JS обновляет SVG: цвет/изображение элемента, значения датчиков
- Состояние симуляции — в БД (`SimulationSession`), не в `HttpSession`

### Файлы (отложено)
```
models/SimulationScenario.java
models/SimulationStep.java
models/SimulationElement.java
models/SimulationSession.java
enumeration/SimulationStatus.java      — NOT_STARTED, IN_PROGRESS, COMPLETED, FAILED, EXPIRED
enumeration/ElementType.java           — VALVE, PUMP, SWITCH, SENSOR_TEMP, SENSOR_PRESSURE, HEATER, LOCK

repo/SimulationScenarioRepository.java
repo/SimulationStepRepository.java
repo/SimulationElementRepository.java
repo/SimulationSessionRepository.java

dto/SimulationScenarioDTO.java
dto/SimulationSessionDTO.java
mapper/SimulationScenarioMapper.java
mapper/SimulationSessionMapper.java

service/employee/chief/ScenarioService.java          — CRUD сценариев и шагов
service/employee/specialist/SimulationEngine.java     — логика проверки шагов, расчёт оценки
service/employee/specialist/MnemoService.java         — управление сессиями симуляции

controllers/employee/chief/ScenarioController.java    — управление сценариями
controllers/employee/specialist/MnemoController.java  — прохождение симуляции

templates/employee/chief/scenarios/
    allScenarios.html       — список сценариев
    addScenario.html        — создание сценария + шаги
    editScenario.html
    detailsScenario.html    — просмотр шагов сценария

templates/employee/specialist/mnemo/
    scenarios.html          — доступные сценарии
    view.html               — SVG-мнемосхема + JS-интерактив + таймер + инструкция текущего шага
    result.html             — итог: пройден/не пройден, оценка

static/svg/pneumo-scheme.svg
static/js/mnemo.js          — логика взаимодействия с SVG, таймер, fetch-запросы
```

---

## Новый функционал

### Статус новых функций

| # | Название | Сложность | Статус |
|---|----------|-----------|--------|
| Н1 | Результаты симуляций (специалист) | SMALL | ✅ Выполнен (PR #14) |
| Н2 | Результаты симуляций (начальник) + Excel | MEDIUM | ✅ Выполнен (PR #14) |
| Н3 | Автозавершение назначения сценария | SMALL | ✅ Выполнен (PR #14) |
| Н4 | Экспорт результатов симуляций в DOCX/Excel | SMALL | ✅ Выполнен (PR #14) |
| Н5 | Адаптивная траектория обучения | MEDIUM | ✅ Выполнен (PR #16) |
| Н6 | Режим ошибок в симуляции | LARGE | ✅ Выполнен (PR #21) |
| Н7 | Конструктор экзамена из банка вопросов | MEDIUM | ⏭ Пропущен |
| Н8 | Пагинация таблиц | SMALL | ✅ Выполнен (PR #15) |
| Н9 | Сортировка таблиц | SMALL | ✅ Выполнен (PR #15) |

### Порядок реализации по этапам

| Этап | Ветка | Функции | Статус |
|------|-------|---------|--------|
| A | `claude/etap-a-simulation-results` | Н1→Н3→Н2→Н4 | ✅ Выполнен (PR #14) |
| B | `claude/etap-b-pagination-sorting` | Н8+Н9 | ✅ Выполнен (PR #15) |
| C | `claude/etap-c-adaptive-learning` | Н5 | ✅ Выполнен (PR #16) |
| fix | `claude/post-etap-c-fixes` | Фиксы 1-3 | ✅ Выполнен (PR #17) |
| D | `claude/etap-d-exam-generator` | Н7 | ⏭ Пропущен |
| E | `claude/etap-e-fault-simulation` | Н6 | ✅ Выполнен (PR #21) |

### Граф зависимостей

```
Н1 → Н2 → Н4       (результаты симуляций → начальник видит → экспорт)
Н1 → Н3             (результаты → автозавершение назначения)
Н5                   (независимый, но нужны данные по результатам тестов)
Н6                   (независимый, расширение симуляции)
Н7                   (независимый, расширение тестирования)
Н8, Н9               (независимые, UI-улучшения, можно в любой момент)
```

---

### Н1. Результаты симуляций для специалиста (SMALL)

**Цель:** специалист видит историю своих прохождений симуляций и детали каждой попытки.

**Текущее состояние:**
- `ResultController.myResults` уже загружает `simSessions` в модель
- `myResults.html` уже содержит вкладку «Симуляции» с таблицей
- `SimulationService.getMyResults(employee)` уже возвращает `List<SimulationSessionDTO>`
- Не хватает: страницы детального просмотра, данных о шагах в DTO

**Что нужно:**

1. **`SimulationSessionDTO`** — добавить поля:
   - `scenarioDescription` (String)
   - `schemaTitle` (String)
   - `stepDetails` (List<StepResultDTO>) — для детального просмотра

2. **Новый DTO `StepResultDTO`**:
   ```
   stepNumber, instructionText, isPassed (boolean)
   ```

3. **`SimulationSession`** — добавить поле:
   - `stepResults` (String, TEXT) — JSON: `[{"step":1,"passed":true},{"step":2,"passed":false}]`
   - Заполняется в `SimulationService.checkStep()` при каждой проверке

4. **`SimulationService`** — новый метод:
   - `getSessionResult(sessionId, employee)` → Optional<SimulationSessionDTO> с проверкой ownership
   - В `checkStep()` — записывать результат каждого шага в `stepResults` JSON

5. **`ResultController`** — новые эндпоинты:
   - `GET /employee/specialist/results/detailsSimResult/{sessionId}` → страница деталей
   - Проверка: сессия принадлежит текущему пользователю

6. **`detailsSimResult.html`** — новый шаблон:
   - Карточка: сценарий, схема, статус, время начала/окончания, шагов пройдено / всего
   - Таблица шагов: #, инструкция, статус (✅/❌)
   - Визуальный прогресс-бар: `completedSteps / totalSteps`
   - Паттерн: аналог `specialist/results/detailsResult.html`

**Файлы: 1 новый шаблон, ~4 изменённых** (DTO, entity, service, controller)

---

### Н2. Результаты симуляций для начальника + Excel (MEDIUM)

**Цель:** начальник видит все результаты симуляций с фильтрами и может экспортировать.

**Что нужно:**

1. **`SimulationReportService`** (новый сервис `service/employee/chief/`):
   - `getAllSimulationResults(employeeId, scenarioId, dateFrom, dateTo)` — фильтрация потоком
   - `getSimulationJournalData()` — матрица: сотрудник × сценарий → последний результат
   - `exportSimulationProtocol(scenarioId)` — Excel: шапка + таблица сессий
   - `exportSimulationJournal()` — Excel матрица

2. **`ReportController`** — новые эндпоинты:
   - `GET /employee/chief/results/allSimResults` — таблица с фильтрами
   - `GET /employee/chief/results/detailsSimResult/{sessionId}` — детали сессии
   - `POST /employee/chief/results/exportSimProtocol` — скачивание Excel
   - `POST /employee/chief/results/exportSimJournal` — скачивание Excel

3. **Шаблоны:**
   - `allSimResults.html` — таблица: #, ФИО, Сценарий, Дата, Шагов, Статус, Действия
     - Фильтры: дата с/по, сценарий (select), сотрудник (select)
     - Кнопки экспорта
   - `detailsSimResult.html` (chief) — аналог specialist, но без проверки ownership

4. **`AnalyticsService`** — интеграция:
   - Новые метрики на дашборде: `simulationSessionsCount`, `simulationPassRate`
   - В `getDashboardData()` добавить: кол-во симуляций за период, % успешных
   - `dashboard.html` — новые карточки метрик + строка в таблице статистики

5. **Sidebar** (`header.html`):
   - В меню начальника добавить вкладку или объединить с существующими результатами

**Excel-протокол симуляций (паттерн ReportService):**
```
Заголовок: "ПРОТОКОЛ прохождения сценария мнемосхемы"
Сценарий: {title}
Схема: {schemaTitle}
Дата: {today}

| # | ФИО | Должность | Дата | Шагов пройдено | Всего шагов | Статус | Время (мин) |
```

**Файлы: 3 новых (сервис + 2 шаблона), ~4 изменённых**

---

### Н3. Автоматическое завершение назначения сценария (SMALL)

**Цель:** при успешном завершении симуляции система помечает назначение как выполненное.

**Текущее состояние:**
- `TestAssignmentService.markAssignmentCompleted(employeeId, testId, session)` — находит PENDING назначения по testId и помечает COMPLETED
- `TestAssignment` уже поддерживает `scenario` (nullable)
- `TestAssignmentEmployee.completedSession` ссылается на `TestSession` — нужна аналогичная ссылка для `SimulationSession`

**Что нужно:**

1. **`TestAssignmentEmployee`** — добавить поле:
   - `completedSimulationSession` (ManyToOne SimulationSession, nullable)

2. **`TestAssignmentService`** — новый метод:
   ```java
   void markScenarioAssignmentCompleted(Long employeeId, Long scenarioId, SimulationSession session)
   ```
   - Находит PENDING записи по `assignment.scenario.id == scenarioId`
   - Ставит `status = COMPLETED`, `completedSimulationSession = session`

3. **`SimulationService.checkStep()`** — при статусе COMPLETED:
   ```java
   if (session.getSessionStatus() == SimulationSessionStatus.COMPLETED) {
       testAssignmentService.markScenarioAssignmentCompleted(
           employee.getId(), session.getScenario().getId(), session);
   }
   ```

4. **`TestAssignmentEmployeeRepository`** — новый метод:
   ```java
   List<TestAssignmentEmployee> findByEmployeeIdAndAssignment_ScenarioIdAndStatus(
       Long employeeId, Long scenarioId, AssignmentStatus status);
   ```

5. **`detailsAssignment.html`** — в таблице сотрудников:
   - Если `completedSimulationSession != null` → ссылка на результат симуляции

**Файлы: ~5 изменённых** (entity, repo, 2 service, template)

---

### Н4. Экспорт результатов симуляций в DOCX/Excel (SMALL)

**Цель:** специалист и начальник могут скачать протокол прохождения сценария.

**Зависимость:** Н1 (нужен `stepResults` JSON в SimulationSession)

**Что нужно:**

1. **`SimulationReportService`** (из Н2, либо в `ResultService`) — метод:
   ```java
   byte[] exportSimulationResultToExcel(Long sessionId, Employee employee)
   ```
   **Структура Excel:**
   ```
   Заголовок: "Результат прохождения сценария мнемосхемы"
   ФИО: {fullName}
   Сценарий: {scenario.title}
   Мнемосхема: {schema.title}
   Дата: {startedAt}     Время: {duration мин}
   Статус: {COMPLETED/FAILED/EXPIRED}
   Шагов пройдено: {completedSteps} из {totalSteps}

   | # | Инструкция | Результат |
   | 1 | Откройте VP1, включите N1 | ✓ Выполнен |
   | 2 | Откройте VP2 | ✗ Ошибка |
   ```

2. **`ResultController`** — новый эндпоинт:
   - `GET /employee/specialist/results/exportSimResult/{sessionId}` → скачивание Excel

3. **`ReportController`** — аналогичный эндпоинт для начальника (без ownership check)

**Файлы: ~3 изменённых** (сервис + 2 контроллера)

---

### Н5. Адаптивная траектория обучения (MEDIUM)

**Цель:** после завершения теста система анализирует ошибки и рекомендует теоретические материалы + повторные тесты по слабым темам. Связывает модули «теория → тесты → аналитика».

**Ключевое решение — связь вопросов с теорией:**
Сейчас `TestQuestion` не связан с `TheorySection`. Нужно добавить связь.

**Что нужно:**

1. **`TestQuestion`** — добавить поле:
   - `theorySection` (ManyToOne TheorySection, nullable) — к какому разделу теории относится вопрос

2. **`addQuestion.html` / `editQuestion.html`** — добавить:
   - `<select>` «Раздел теории» (опционально) — список TheorySection

3. **`TestQuestionController`** — передавать `allSections` в модель

4. **Новый сервис `LearningPathService`** (`service/employee/specialist/`):
   ```java
   LearningRecommendationDTO getRecommendations(Long sessionId, Employee employee)
   ```
   **Алгоритм:**
   - Загрузить `TestSessionAnswer` для сессии
   - Найти неправильные ответы → достать их `TestQuestion.theorySection`
   - Сгруппировать по разделам теории
   - Для каждого слабого раздела:
     - Список материалов из этого раздела (ссылки)
     - Другие тесты, содержащие вопросы из этого раздела (рекомендация к прохождению)
   - Если 100% правильных → "Отлично, слабых тем не выявлено"

5. **Новый DTO `LearningRecommendationDTO`**:
   ```
   totalQuestions, correctCount, wrongCount
   List<WeakTopicDTO>:
       sectionId, sectionTitle
       wrongCount, totalInSection
       List<TheoryMaterialDTO> materials   — рекомендованные материалы
       List<TestDTO> suggestedTests        — тесты по этой теме
   ```

6. **`result.html` (specialist/testing)** — добавить блок:
   - Секция «Рекомендации по обучению» (после основных результатов)
   - Для каждой слабой темы:
     - Название раздела + кол-во ошибок
     - Ссылки на материалы: `<a href="/employee/specialist/theory/viewMaterial/{id}">`
     - Ссылка на рекомендуемый тест: `<a href="/employee/specialist/testing/startTest/{id}">`
   - Если ошибок нет → "Поздравляем! Ошибок не выявлено."

7. **`ResultController`** / **`TestingController`** — в `result()` добавить:
   ```java
   model.addAttribute("recommendations", learningPathService.getRecommendations(sessionId, currentUser));
   ```

8. **Sidebar** — опционально: пункт «Мои рекомендации» со списком всех незакрытых слабых тем

**Файлы: 1 новый сервис, 2 новых DTO, ~6 изменённых** (entity, controller, templates)

---

### Н6. Режим ошибок в симуляции (LARGE)

**Цель:** добавить аварийные события в сценарии: отказ клапана, неверное давление, ограничение по времени на шаг, штрафы за опасные действия. Делает тренажёр реалистичным.

**Ключевые подфичи:**

#### Н6.1. Аварийные события на шаге

**`ScenarioStep`** — новые поля:
- `faultEvent` (String, TEXT, nullable) — JSON описания аварии:
  ```json
  {
    "type": "ELEMENT_FAILURE",
    "elementName": "VP7",
    "message": "Клапан VP7 заклинил в закрытом положении!",
    "lockElement": true
  }
  ```
- `stepTimeLimit` (Integer, nullable) — ограничение по времени на шаг (секунды), null = без ограничения

**Типы аварийных событий (enum `FaultEventType`):**
| Тип | Описание | Эффект |
|-----|----------|--------|
| `ELEMENT_FAILURE` | Отказ элемента | Элемент заблокирован (не переключается), подсвечен красным |
| `PRESSURE_ANOMALY` | Аномальное давление | Датчик показывает критическое значение, нужно среагировать |
| `OVERHEAT` | Перегрев | Датчик температуры выше нормы, нужно отключить нагреватель |
| `FALSE_ALARM` | Ложное срабатывание | Индикатор мигает, но реальной аварии нет (проверка на хладнокровие) |

#### Н6.2. Штрафы за опасные действия

**`ScenarioStep`** — новое поле:
- `forbiddenActions` (String, TEXT, nullable) — JSON:
  ```json
  [
    {"elementName": "VP5", "action": "on", "penalty": "FAIL", "message": "Открытие VP5 при работающем N1 вызовет гидроудар!"},
    {"elementName": "NR1", "action": "off", "penalty": "WARNING", "message": "Отключение NR1 без сброса давления опасно"}
  ]
  ```

**Типы штрафов (enum `PenaltyType`):**
| Тип | Эффект |
|-----|--------|
| `FAIL` | Немедленный провал сценария |
| `WARNING` | Предупреждение + запись в лог сессии (не блокирует) |
| `TIME_PENALTY` | Уменьшение оставшегося времени на N секунд |

#### Н6.3. Реализация на бэкенде

**`SimulationService`** — изменения:

- `toggleElement()`:
  1. Проверить `forbiddenActions` текущего шага
  2. Если действие запрещено → применить штраф, вернуть `{"forbidden": true, "penalty": "FAIL", "message": "..."}`
  3. Если элемент заблокирован `faultEvent.lockElement` → вернуть `{"locked": true, "message": "Элемент неисправен"}`

- `checkStep()`:
  1. Проверить `stepTimeLimit` — если время шага истекло → FAILED
  2. Стандартная кумулятивная проверка

- `advanceToStep()` (вызывается при переходе к следующему шагу):
  1. Если новый шаг содержит `faultEvent` → применить эффект:
     - `ELEMENT_FAILURE`: заблокировать элемент в `currentState`, записать в сессию
     - `PRESSURE_ANOMALY`: передать клиенту значение для отображения
  2. Вернуть информацию об аварии клиенту

**`SimulationSession`** — новые поля:
- `lockedElements` (String, TEXT) — JSON список заблокированных элементов
- `warnings` (String, TEXT) — JSON лог предупреждений `[{"step":2, "message":"..."}]`
- `stepStartedAt` (LocalDateTime) — время начала текущего шага (для stepTimeLimit)

#### Н6.4. Реализация на фронтенде (`simulation.js`)

- **Заблокированный элемент**: красная рамка + иконка ⚠️, при клике → сообщение «Элемент неисправен»
- **Аварийное событие**: модальное окно / alert-banner с описанием аварии при переходе на шаг
- **Штраф WARNING**: toast-уведомление жёлтого цвета
- **Штраф FAIL**: модальное окно → redirect на result
- **Таймер шага**: дополнительный мини-таймер рядом с инструкцией (если `stepTimeLimit` задан)
- **Аномальные показания датчиков**: мигающий красный цвет значения

#### Н6.5. Конструктор (editScenario.html / scenarioEditor.js)

- В каждом шаге добавить секции:
  - «Аварийное событие» — выбор типа + элемент + сообщение
  - «Запрещённые действия» — список: элемент + действие + тип штрафа + сообщение
  - «Ограничение времени на шаг» — input number (секунды)

**Файлы: ~3 новых (enum, DTO), ~8 изменённых** (entity, service, JS, templates)

---

### Н7. Конструктор экзамена из банка вопросов (MEDIUM)

**Цель:** система автоматически собирает тест из банка вопросов по заданным критериям (темы, сложность, количество). Не заменяет ручное создание тестов, а дополняет.

**Ключевое решение — сложность и темы:**
Нужно добавить к вопросам метаданные.

**Что нужно:**

1. **`TestQuestion`** — новые поля:
   - `difficulty` (Integer, default 1) — сложность 1-5
   - `theorySection` (ManyToOne TheorySection, nullable) — раздел теории (если не добавлен в Н5)
   - `isShared` (boolean, default false) — доступен для включения в генерируемые тесты

2. **Новый enum `ExamGenerationMode`**:
   - `RANDOM` — случайная выборка из банка
   - `BALANCED` — равномерно по темам
   - `DIFFICULTY_PROGRESSIVE` — от лёгких к сложным

3. **Новый сервис `ExamGeneratorService`** (`service/employee/chief/`):
   ```java
   Test generateExam(ExamGenerationParams params, Employee createdBy)
   ```

4. **DTO `ExamGenerationParams`**:
   ```
   title, description, timeLimit, passingScore, isExam
   generationMode (ExamGenerationMode)
   totalQuestions (int)
   sectionCriteria (List<SectionCriterion>):
       sectionId, questionCount, minDifficulty, maxDifficulty
   departmentIds (List<Long>)
   ```

5. **Алгоритм генерации:**
   ```
   1. Собрать пул: все вопросы с isShared=true
   2. Фильтровать по sectionCriteria (раздел + сложность)
   3. Применить режим:
      - RANDOM: shuffle + take(N)
      - BALANCED: из каждого раздела по N/sections штук
      - DIFFICULTY_PROGRESSIVE: сортировка по difficulty, take(N)
   4. Создать новый Test с копиями вопросов (не ссылками, чтобы оригиналы можно было менять)
   5. Назначить allowedDepartments
   ```

6. **Controller** — новые эндпоинты в `TestController`:
   - `GET /employee/chief/tests/generateExam` — форма параметров генерации
   - `POST /employee/chief/tests/generateExam` — генерация → redirect на editTest/{id}

7. **`generateExam.html`** — новый шаблон:
   - Основные поля (название, описание, лимит, проходной балл)
   - Режим генерации (radio)
   - Количество вопросов (input number)
   - Настройка по разделам: для каждого TheorySection — кол-во вопросов, мин/макс сложность
   - Кнопка «Сгенерировать» → создаёт тест → открывает его в editTest для ручной правки

8. **`addQuestion.html` / `editQuestion.html`** — добавить:
   - `<select>` сложности (1-5 звёзд)
   - `<select>` раздела теории
   - `<checkbox>` «Доступен для банка вопросов»

9. **`allTests.html`** — кнопка «Сгенерировать экзамен» рядом с «Добавить тест»

**Файлы: 1 новый сервис, 2 новых DTO/enum, 1 новый шаблон, ~6 изменённых**

---

### Н8. Пагинация таблиц (SMALL)

**Цель:** клиентская пагинация для таблиц с большим количеством строк.

**Подход:** единый JS-компонент, без изменений бэкенда. Thymeleaf рендерит все строки, JS скрывает лишние и показывает навигацию.

**Что нужно:**

1. **`static/js/tablePagination.js`** (~80 строк):
   ```javascript
   function initPagination(tableId, options) {
       // options: { perPage: 20, showInfo: true }
       // 1. Найти <tbody> таблицы
       // 2. Скрыть все строки кроме первых perPage
       // 3. Создать навигацию: « 1 2 3 ... N »
       // 4. Кнопки переключения: показать/скрыть строки
       // 5. Инфо: "Показано 1-20 из 157"
   }
   ```

2. **Подключение** — добавить в нужные страницы:
   ```html
   <script src="/js/tablePagination.js"></script>
   <script>initPagination('resultsTable', {perPage: 20});</script>
   ```

3. **Целевые таблицы:**
   - `chief/results/allResults.html`
   - `chief/assignments/allAssignments.html`
   - `admin/users/allUsers.html`
   - `specialist/results/myResults.html`
   - `chief/results/allSimResults.html` (из Н2)

4. **Стили** (`style.css`):
   - `.pagination-wrapper` — flex, gap, align-center
   - `.pagination-btn` — стиль кнопок (active, disabled)
   - `.pagination-info` — text-muted small

**Файлы: 1 новый JS, ~5 изменённых шаблонов, CSS**

---

### Н9. Сортировка таблиц (SMALL)

**Цель:** клик по заголовку колонки → сортировка таблицы (по возрастанию/убыванию).

**Подход:** клиентский JS, без бэкенда. Совместим с пагинацией из Н8.

**Что нужно:**

1. **`static/js/tableSort.js`** (~60 строк):
   ```javascript
   function initSortable(tableId) {
       // 1. Найти все <th> с атрибутом data-sortable
       // 2. Добавить иконку ↕ и cursor:pointer
       // 3. По клику:
       //    - Определить тип данных: data-sort-type="text|number|date|percent"
       //    - Собрать значения из <td> по индексу колонки
       //    - Отсортировать строки <tr> в <tbody>
       //    - Переключить направление (asc ↔ desc)
       //    - Обновить иконку: ↑ или ↓
       //    - Если есть пагинация → пересчитать
   }
   ```

2. **Разметка в шаблонах** — добавить `data-sortable` и `data-sort-type`:
   ```html
   <th data-sortable data-sort-type="text">ФИО</th>
   <th data-sortable data-sort-type="date">Дата</th>
   <th data-sortable data-sort-type="number">Баллы</th>
   <th data-sortable data-sort-type="percent">%</th>
   ```

3. **Типы сортировки:**
   - `text` — `localeCompare('ru')`
   - `number` — `parseFloat()`
   - `date` — парсинг `dd.MM.yyyy` → Date
   - `percent` — извлечение числа из `85%` → parseFloat

4. **Стили** (`style.css`):
   - `th[data-sortable]` — cursor:pointer, user-select:none
   - `th[data-sortable]:hover` — background чуть темнее
   - `.sort-icon` — margin-left:4px, opacity:0.5
   - `.sort-asc .sort-icon`, `.sort-desc .sort-icon` — opacity:1

5. **Целевые таблицы:** те же, что в Н8

**Файлы: 1 новый JS, ~5 изменённых шаблонов, CSS**

---

### Рекомендуемый порядок реализации

```
Этап A (базовые результаты):  Н1 → Н3 → Н2 → Н4
Этап B (UI-улучшения):        Н8 + Н9 (параллельно)
Этап C (обучение):            Н5 (адаптивная траектория)
Этап D (тесты):               Н7 (конструктор экзамена)
Этап E (симуляция):            Н6 (режим ошибок)
```

**Этап A** закрывает логический пробел — сценарии назначаются, но результаты не отслеживаются.
**Этап B** — быстрые визуальные улучшения, можно делать в любой момент.
**Этап C** — связывает теорию и тесты, сильный аргумент на защите.
**Этап D** — расширяет тестирование, автоматизация.
**Этап E** — самый объёмный, но самый впечатляющий для комиссии.

### Итого файлов по новому функционалу

| Функция | Новых | Изменённых | Сложность |
|---------|-------|------------|-----------|
| Н1 Результаты симуляций (спец.) | 1 | 4 | SMALL |
| Н2 Результаты симуляций (chief) | 3 | 4 | MEDIUM |
| Н3 Автозавершение назначения | 0 | 5 | SMALL |
| Н4 Экспорт симуляций | 0 | 3 | SMALL |
| Н5 Адаптивная траектория | 3 | 6 | MEDIUM |
| Н6 Режим ошибок | 3 | 8 | LARGE |
| Н7 Конструктор экзамена | 4 | 6 | MEDIUM |
| Н8 Пагинация | 1 | 6 | SMALL |
| Н9 Сортировка | 1 | 6 | SMALL |
| **Итого** | **~16** | **~48** | |
