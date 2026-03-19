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
