# CODESTYLE.md — Правила написания кода для АОС ПИ

> Этот файл читается вместе с `CLAUDE.md` при **написании кода**.
> При планировании или обсуждении архитектуры его можно не грузить.

---

## Архитектура

```
Controller → Service → Repository → Entity (JPA)
                         ↕
                    DTO + Mapper (MapStruct)
```

- **Controller** — тонкий: приём параметров из формы, вызов сервиса, формирование `Model`, return шаблон или `redirect:`
- **Service** — ВСЯ бизнес-логика: валидация, проверки уникальности, каскадные операции, `@Transactional`, логирование
- **Repository** — только доступ к данным: `JpaRepository`, `@Query` JPQL
- **Entity** — JPA-сущность, `@Transient` для вычисляемых полей
- **DTO** — плоское представление, без бизнес-логики, класс с `@Data` (НЕ record)
- **Mapper** — MapStruct, `Mappers.getMapper()` (без Spring DI)

---

## Порядок добавления новой сущности

1. Entity → `models/{EntityName}.java`
2. Repository → `repo/{EntityName}Repository.java`
3. DTO → `dto/{EntityName}DTO.java`
4. Mapper → `mapper/{EntityName}Mapper.java`
5. Enum → `enumeration/{EntityName}Status.java` (если нужен)
6. Service → `service/employee/{role}/{EntityName}Service.java`
7. Controller → `controllers/employee/{role}/{EntityName}Controller.java`
8. HTML → `templates/employee/{role}/{entities}/` (all, add, edit, details)
9. Навигация → добавить ссылку в header.html
10. SQL → seed-данные при необходимости

---

## Паттерны кода

### Entity

```java
@Data
@NoArgsConstructor
@Entity
@Table(name = "t_entityName")
@EqualsAndHashCode(of = "id")
public class EntityName {

    // ========== ПОЛЯ ==========

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** [Описание поля на русском] */
    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String uniqueField;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntityStatus status;

    @ManyToOne
    @JoinColumn(nullable = false)
    private RelatedEntity related;

    @ToString.Exclude
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChildEntity> children = new ArrayList<>();

    // ========== КОНСТРУКТОРЫ ==========

    // ========== МЕТОДЫ ==========

    @Transient
    public int getComputedField() { return ...; }
}
```

Правила:
- `@ToString.Exclude` на ВСЕХ коллекциях и обратных ManyToOne-ссылках
- `@EqualsAndHashCode(of = "id")` — всегда только по id
- Javadoc на русском для каждого поля
- Секции с маркерами `// ========== ПОЛЯ ==========`
- Таблица БД: префикс `t_` + camelCase (`t_product`, `t_clientOrder`)

---

### Repository

```java
public interface EntityNameRepository extends JpaRepository<EntityName, Long> {

    boolean existsByUniqueField(String value);
    boolean existsByUniqueFieldAndIdNot(String value, Long id);  // для edit

    @Query("SELECT e FROM EntityName e WHERE LOWER(e.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<EntityName> findByName(@Param("search") String search, Pageable pageable);
}
```

---

### DTO

```java
@Data
public class EntityNameDTO {
    private Long id;
    private String name;

    // Развёрнутые поля из связей
    private Long relatedId;        // из related.id
    private String relatedName;    // из related.name

    // Enum как displayName при необходимости
    private String statusDisplayName;
}
```

---

### Mapper

```java
@Mapper
public interface EntityNameMapper {
    EntityNameMapper INSTANCE = Mappers.getMapper(EntityNameMapper.class);

    @Mapping(source = "related.id", target = "relatedId")
    @Mapping(source = "related.name", target = "relatedName")
    @Mapping(source = "entity", target = "computedField", qualifiedByName = "computedField")
    EntityNameDTO toDTO(EntityName entity);

    List<EntityNameDTO> toDTOList(List<EntityName> entities);

    @Named("computedField")
    default int getComputedField(EntityName entity) {
        return entity.getComputedField();
    }
}
```

---

### Service

```java
@Service
@Getter
@Slf4j
public class EntityNameService {

    private final EntityNameRepository entityNameRepository;
    private final RelatedEntityRepository relatedEntityRepository;

    public EntityNameService(EntityNameRepository entityNameRepository,
                             RelatedEntityRepository relatedEntityRepository) {
        this.entityNameRepository = entityNameRepository;
        this.relatedEntityRepository = relatedEntityRepository;
    }

    public boolean checkUniqueField(String value, Long id) {
        if (value == null || value.isBlank()) return false;
        if (id != null) return entityNameRepository.existsByUniqueFieldAndIdNot(value, id);
        return entityNameRepository.existsByUniqueField(value);
    }

    @Transactional
    public Optional<Long> saveEntityName(EntityName entity, Long relatedId) {
        // 1. Проверка уникальности
        if (checkUniqueField(entity.getUniqueField(), null)) {
            log.error("Запись с таким значением уже существует: {}", entity.getUniqueField());
            return Optional.empty();
        }

        // 2. Загрузка связанных сущностей
        Optional<RelatedEntity> relatedOptional = relatedEntityRepository.findById(relatedId);
        if (relatedOptional.isEmpty()) {
            log.error("Связанная сущность с id={} не найдена.", relatedId);
            return Optional.empty();
        }
        entity.setRelated(relatedOptional.get());

        // 3. Сохранение
        try {
            entityNameRepository.save(entity);
        } catch (Exception e) {
            log.error("Ошибка при сохранении: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }

        log.info("Сущность сохранена: id={}", entity.getId());
        return Optional.of(entity.getId());
    }

    @Transactional
    public Optional<Long> editEntityName(Long id, String inputField) {
        Optional<EntityName> entityOptional = entityNameRepository.findById(id);
        if (entityOptional.isEmpty()) {
            log.error("Сущность с id={} не найдена.", id);
            return Optional.empty();
        }
        EntityName entity = entityOptional.get();
        entity.setName(inputField);

        try {
            entityNameRepository.save(entity);
        } catch (Exception e) {
            log.error("Ошибка при обновлении: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
        return Optional.of(entity.getId());
    }

    @Transactional
    public boolean deleteEntityName(Long id) {
        Optional<EntityName> entityOptional = entityNameRepository.findById(id);
        if (entityOptional.isEmpty()) {
            log.error("Сущность с id={} не найдена.", id);
            return false;
        }

        // Проверка зависимостей перед удалением
        // if (childRepository.countByEntityNameId(id) > 0) { log.error(...); return false; }

        try {
            entityNameRepository.delete(entityOptional.get());
        } catch (Exception e) {
            log.error("Ошибка при удалении: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
        return true;
    }

    @Transactional
    public List<EntityNameDTO> getAllEntityNames() {
        return EntityNameMapper.INSTANCE.toDTOList(entityNameRepository.findAll());
    }
}
```

Правила:
- Аннотации: `@Service`, `@Getter`, `@Slf4j`
- DI только через конструктор
- `@Transactional` на всех методах записи
- Возврат: `Optional<Long>` для save/edit, `boolean` для delete
- Никогда не бросать exceptions — только `return false` / `Optional.empty()`
- `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()` в catch
- Одноимённые сервисы → `@Service("roleEntityNameService")`

---

### Controller

```java
@Controller
@Slf4j
public class EntityNameController {

    private final EntityNameService entityNameService;

    public EntityNameController(EntityNameService entityNameService) {
        this.entityNameService = entityNameService;
    }

    // AJAX-проверка уникальности
    @GetMapping("/employee/role/entities/check-uniqueField")
    public ResponseEntity<Map<String, Boolean>> checkUniqueField(
            @RequestParam String value, @RequestParam(required = false) Long id) {
        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", entityNameService.checkUniqueField(value, id));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/employee/role/entities/allEntities")
    public String allEntities(Model model) {
        model.addAttribute("allEntities", entityNameService.getAllEntityNames().stream()
                .sorted(Comparator.comparing(EntityNameDTO::getName))
                .toList());
        return "employee/role/entities/allEntities";
    }

    @GetMapping("/employee/role/entities/addEntity")
    public String addEntity(Model model) {
        // Добавить в model справочники если нужны
        return "employee/role/entities/addEntity";
    }

    @PostMapping("/employee/role/entities/addEntity")
    public String addEntity(@RequestParam String inputName,
                            @RequestParam Long inputRelatedId,
                            Model model) {
        EntityName entity = new EntityName();
        entity.setName(inputName);

        Optional<Long> savedId = entityNameService.saveEntityName(entity, inputRelatedId);
        if (savedId.isEmpty()) {
            model.addAttribute("entityError", "Ошибка при сохранении.");
            return "employee/role/entities/addEntity";
        }
        return "redirect:/employee/role/entities/detailsEntity/" + savedId.get();
    }

    @GetMapping("/employee/role/entities/detailsEntity/{id}")
    public String detailsEntity(@PathVariable(value = "id") long id, Model model) {
        if (!entityNameService.getEntityNameRepository().existsById(id)) {
            return "redirect:/employee/role/entities/allEntities";
        }
        EntityName entity = entityNameService.getEntityNameRepository().findById(id).get();
        model.addAttribute("entityDTO", EntityNameMapper.INSTANCE.toDTO(entity));
        return "employee/role/entities/detailsEntity";
    }

    @GetMapping("/employee/role/entities/editEntity/{id}")
    public String editEntity(@PathVariable(value = "id") long id, Model model) {
        if (!entityNameService.getEntityNameRepository().existsById(id)) {
            return "redirect:/employee/role/entities/allEntities";
        }
        EntityName entity = entityNameService.getEntityNameRepository().findById(id).get();
        model.addAttribute("entityDTO", EntityNameMapper.INSTANCE.toDTO(entity));
        return "employee/role/entities/editEntity";
    }

    @PostMapping("/employee/role/entities/editEntity/{id}")
    public String editEntity(@PathVariable(value = "id") long id,
                             @RequestParam String inputName,
                             RedirectAttributes redirectAttributes) {
        Optional<Long> editedId = entityNameService.editEntityName(id, inputName);
        if (editedId.isEmpty()) {
            redirectAttributes.addFlashAttribute("entityError", "Ошибка при сохранении.");
            return "redirect:/employee/role/entities/editEntity/" + id;
        }
        return "redirect:/employee/role/entities/detailsEntity/" + editedId.get();
    }

    @GetMapping("/employee/role/entities/deleteEntity/{id}")
    public String deleteEntity(@PathVariable(value = "id") long id,
                               RedirectAttributes redirectAttributes) {
        if (!entityNameService.deleteEntityName(id)) {
            redirectAttributes.addFlashAttribute("deleteError", "Невозможно удалить запись.");
            return "redirect:/employee/role/entities/detailsEntity/" + id;
        }
        return "redirect:/employee/role/entities/allEntities";
    }
}
```

Правила:
- `@Controller` (не `@RestController` для HTML)
- `@PathVariable(value = "id") long id` — всегда с `value = "id"`
- `@RequestParam` с префиксом `input`: `inputName`, `inputCategoryId`, `inputFileField`
- Redirect после POST: details при успехе, форма при ошибке
- При несуществующем id → redirect на allEntities
- Одноимённые контроллеры → `@Controller("roleEntityNameController")`

---

### Enum

```java
@Getter
public enum EntityStatus {
    NEW("Новый"),
    IN_PROGRESS("В работе"),
    COMPLETED("Завершён"),
    CANCELLED("Отменён");

    private final String displayName;

    EntityStatus(String displayName) {
        this.displayName = displayName;
    }
}
```

---

## HTML-шаблоны (Thymeleaf + Bootstrap)

### Каждая страница

```html
<!DOCTYPE HTML>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>Заголовок | АОС ПИ</title>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link href="/css/bootstrap.min.css" rel="stylesheet">
    <link href="/css/style.css" rel="stylesheet">
    <script src="/js/bootstrap.bundle.min.js"></script>
</head>
<body>
<header th:insert="~{blocks/header :: header}"></header>
<!-- содержимое -->
<div th:insert="~{blocks/footer :: footer}"></div>
<script>/* vanilla JS */</script>
</body>
</html>
```

### Список (allEntities.html)

```html
<div class="container my-5">
    <div class="profile-card mb-4">
        <div class="profile-actions">
            <a th:href="'/'" class="btn btn-secondary btn-profile">← Главная</a>
            <a th:href="'/employee/role/entities/addEntity'" class="btn btn-success btn-profile">+ Добавить</a>
        </div>
    </div>
    <div class="profile-card p-4">
        <h2 class="h3 mb-4 fw-bold">Список [сущностей]</h2>
        <div class="table-responsive">
            <table class="table table-modern">
                <thead>
                    <tr><th>№</th><th>Название</th><th>Поле</th></tr>
                </thead>
                <tbody>
                    <tr th:each="item, index : ${allEntities}">
                        <td th:text="${index.index + 1}"></td>
                        <td><a th:text="${item.getName()}"
                               th:href="'/employee/role/entities/detailsEntity/' + ${item.getId()}"></a></td>
                        <td th:text="${item.getField()}"></td>
                    </tr>
                </tbody>
            </table>
        </div>
    </div>
</div>
```

### Форма добавления/редактирования

```html
<div class="container my-5">
    <div class="row justify-content-center">
        <div class="col-12 col-lg-10">
            <div class="profile-card p-4">
                <form action="/employee/role/entities/addEntity" method="post" class="auth-form">
                    <h1 class="page-title">Добавление [сущности]</h1>
                    <p class="page-subtitle">Создайте новую запись</p>

                    <div th:if="${entityError}" class="alert alert-danger" th:text="${entityError}"></div>

                    <div class="mb-4">
                        <h6 class="form-section-title">Основные данные</h6>
                        <div class="row g-3">
                            <div class="col-md-6">
                                <input type="text" class="form-control" id="inputName"
                                       name="inputName" required placeholder="Название" maxlength="255">
                                <div id="nameError" class="invalid-feedback" style="display:none">
                                    Такое значение уже существует
                                </div>
                            </div>
                        </div>
                    </div>

                    <button type="submit" id="submitBtn" class="btn btn-success btn-profile">Сохранить</button>
                </form>
                <div class="link-section mt-3">
                    <a th:href="'/employee/role/entities/allEntities'">← К списку</a>
                </div>
            </div>
        </div>
    </div>
</div>
```

Для **editEntity**: `th:action="'/path/editEntity/' + ${entityDTO.getId()}"`, поля с `th:value="${entityDTO.getField()}"`, кнопка «Сохранить изменения».

### Просмотр (detailsEntity.html)

```html
<div class="container my-5">
    <div class="row justify-content-center">
        <div class="col-12 col-lg-10">
            <div class="profile-card p-4">
                <h1 class="page-title">Информация о [сущности]</h1>

                <div th:if="${deleteError}" class="alert alert-danger" th:text="${deleteError}"></div>

                <div class="mb-4">
                    <h6 class="form-section-title">Основные данные</h6>
                    <div class="row g-3">
                        <div class="col-md-6">
                            <label class="form-label text-muted">Название</label>
                            <input type="text" class="form-control info-display"
                                   th:value="${entityDTO.getName()}" disabled>
                        </div>
                    </div>
                </div>

                <!-- Связанные данные -->
                <div class="mb-4" th:if="${relatedItems != null and !relatedItems.isEmpty()}">
                    <h6 class="form-section-title">Связанные записи</h6>
                    <div class="table-responsive">
                        <table class="table table-sm table-striped">
                            <thead><tr><th>Поле</th></tr></thead>
                            <tbody>
                                <tr th:each="item : ${relatedItems}">
                                    <td th:text="${item.getField()}"></td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>

                <div class="action-buttons mb-4">
                    <button th:onclick="'window.location.href=\'/path/editEntity/' + ${entityDTO.getId()} + '\';'"
                            class="btn btn-primary btn-profile">Редактировать</button>
                    <button class="btn btn-danger btn-profile"
                            th:data-entityid="${entityDTO.getId()}"
                            th:data-name="${entityDTO.getName()}"
                            onclick="confirmDelete(this)">Удалить</button>
                </div>

                <div class="link-section">
                    <a th:href="'/employee/role/entities/allEntities'">← К списку</a>
                </div>
            </div>
        </div>
    </div>
</div>
```

### Полезные фрагменты Thymeleaf

```html
<!-- Дата -->
th:text="${#temporals.format(date, 'dd.MM.yyyy')}"

<!-- Enum -->
th:text="${entity.getStatus().getDisplayName()}"

<!-- Ошибки -->
<div th:if="${entityError}" class="alert alert-danger" th:text="${entityError}"></div>
<div th:if="${deleteError}" class="alert alert-danger" th:text="${deleteError}"></div>
<div th:if="${successMessage}" class="alert alert-success" th:text="${successMessage}"></div>

<!-- Нумерация строк -->
<tr th:each="item, index : ${allEntities}">
    <td th:text="${index.index + 1}"></td>
```

---

## AJAX-валидация уникальности

```javascript
const input = document.getElementById('inputUniqueField');
const errorDiv = document.getElementById('uniqueFieldError');
const submitBtn = document.getElementById('submitBtn');

input.addEventListener('input', function () {
    const value = input.value.trim();
    if (value.length === 0) {
        errorDiv.style.display = 'none';
        input.classList.remove('is-invalid', 'is-valid');
        submitBtn.disabled = false;
        return;
    }
    fetch('/employee/role/entities/check-uniqueField?value=' + encodeURIComponent(value))
        .then(r => r.json())
        .then(data => {
            if (data.exists) {
                errorDiv.style.display = 'block';
                input.classList.add('is-invalid');
                input.classList.remove('is-valid');
                submitBtn.disabled = true;
            } else {
                errorDiv.style.display = 'none';
                input.classList.remove('is-invalid');
                input.classList.add('is-valid');
                submitBtn.disabled = false;
            }
        });
});
```

---

## Подтверждение удаления

```javascript
function confirmDelete(button) {
    const id = button.getAttribute('data-entityid');
    const name = button.getAttribute('data-name');
    if (confirm('Вы точно хотите удалить «' + name + '»?')) {
        window.location.href = '/employee/role/entities/deleteEntity/' + id;
    }
}
```

---

## Цветовая схема CSS

Не менять. Используемые классы:

- Карточка: `profile-card` (белый фон, `border-radius: 15px`, тень)
- Заголовок страницы: `page-title`, `page-subtitle`
- Форма: `auth-form`
- Секция формы: `form-section-title`
- Кнопки: `btn-profile` + `btn-success`/`btn-primary`/`btn-danger`/`btn-secondary`
- Таблица: `table-modern` (с градиентным заголовком `#667eea → #764ba2`)
- Read-only поля: `info-display`
- Панель кнопок: `profile-actions`, `action-buttons`
- Ссылка назад: `link-section`

Градиент: `linear-gradient(135deg, #667eea 0%, #764ba2 100%)`

---

## Логирование

- `@Slf4j` в каждом сервисе
- `log.info()` — успешные операции
- `log.error()` — все ошибки с параметрами: `log.error("Сущность с id={} не найдена.", id)`
- `log.debug()` — детали
- Конфигурация: `logback.xml`, файл `logs/aospi.log`

---

## Жёсткие ограничения

### Запрещено

1. `@Autowired` (field/setter injection) — только конструктор
2. `record` для DTO — только `class` с `@Data`
3. Бросать `Exception` из Service — только `return false` / `Optional.empty()`
4. `@RestController` для HTML-страниц — только `@Controller`
5. Spring `componentModel` в MapStruct
6. jQuery — только vanilla JS
7. Подпакеты внутри `dto/`, `mapper/`, `models/`, `repo/`
8. `th:object` + `th:field` — только `name` + `@RequestParam`
9. Менять цветовую схему CSS

### Обязательно

1. `@ToString.Exclude` на всех коллекциях и обратных ссылках в Entity
2. `@EqualsAndHashCode(of = "id")` в Entity
3. Javadoc на русском в Entity
4. `t_` префикс у всех таблиц
5. `@PathVariable(value = "id") long id` — всегда с `value = "id"`
6. Redirect после POST: details при успехе, форма при ошибке
7. AJAX-проверка всех уникальных полей
8. `confirm()` перед удалением
9. `try-catch` + `setRollbackOnly()` в каждом `@Transactional`-методе
