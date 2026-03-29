-- MySQL 8+
-- Создание мнемосхемы пневмощитка 8У016 на основе документации заказчика.
-- Схема включает: фильтр, редукторы ВД и НД, предохранительный клапан,
-- обратный клапан дренажа, манометры с реальными диапазонами, запорные вентили.
-- + штатный сценарий подготовки к работе и аварийный сценарий (превышение давления).

SET NAMES utf8mb4;

START TRANSACTION;

-- ====== Параметры ======
SET @created_by_id   := 1;
SET @department_id   := 3;
SET @schema_title    := CONVERT('Пневмощиток 8У016' USING utf8mb4) COLLATE utf8mb4_general_ci;
SET @scenario_title  := CONVERT('Подготовка 8У016 к работе' USING utf8mb4) COLLATE utf8mb4_general_ci;
SET @fault_title     := CONVERT('Превышение давления на выходе редуктора' USING utf8mb4) COLLATE utf8mb4_general_ci;

-- ====== Очистка (если повторный запуск) ======
DELETE ss FROM t_simulation_session ss
JOIN t_simulation_scenario sc ON sc.id = ss.scenario_id
JOIN t_mnemo_schema ms ON ms.id = sc.schema_id
WHERE ms.title = @schema_title;

DELETE sd FROM t_scenario_department sd
JOIN t_simulation_scenario sc ON sc.id = sd.scenario_id
JOIN t_mnemo_schema ms ON ms.id = sc.schema_id
WHERE ms.title = @schema_title;

DELETE st FROM t_scenario_step st
JOIN t_simulation_scenario sc ON sc.id = st.scenario_id
JOIN t_mnemo_schema ms ON ms.id = sc.schema_id
WHERE ms.title = @schema_title;

-- Сначала дочерние (аварийные), потом родительские
DELETE sc FROM t_simulation_scenario sc
JOIN t_mnemo_schema ms ON ms.id = sc.schema_id
WHERE ms.title = @schema_title AND sc.parent_scenario_id IS NOT NULL;

DELETE sc FROM t_simulation_scenario sc
JOIN t_mnemo_schema ms ON ms.id = sc.schema_id
WHERE ms.title = @schema_title;

DELETE cn FROM t_schema_connection cn
JOIN t_mnemo_schema ms ON ms.id = cn.schema_id
WHERE ms.title = @schema_title;

DELETE el FROM t_schema_element el
JOIN t_mnemo_schema ms ON ms.id = el.schema_id
WHERE ms.title = @schema_title;

DELETE FROM t_mnemo_schema WHERE title = @schema_title;

-- ====== Создание схемы ======
-- Упрощённая схема пневмощитка 8У016 по документации.
-- Поток: Подвод ВД → Фильтр → Вентиль ВД → Редуктор АУМО-400 → Редуктор АУ311-300 → Распределение
-- Ответвления: предохранительный клапан, дренажная линия с обратным клапаном.
INSERT INTO t_mnemo_schema (title, description, width, height, created_by_id)
VALUES (
    @schema_title,
    'Пневмощиток изделия 8У016 для пневмоиспытаний ДУ РКН. На основе документации заказчика (раздел II, фиг.1).',
    1500,
    750,
    @created_by_id
);

SET @schema_id := LAST_INSERT_ID();

-- ====== Элементы схемы ======
-- Расположение слева направо по ходу потока воздуха.
-- min_value/max_value задают реальные диапазоны манометров из документации.
INSERT INTO t_schema_element
    (name, element_type, posx, posy, width, height, initial_state, rotation, min_value, max_value, schema_id)
VALUES
    -- === Надписи ===
    ('Подвод 230 ати',  'LABEL',  30,  140, 120, 30, false, 0, NULL, NULL, @schema_id),
    ('АУМО-400',        'LABEL', 490,  140, 100, 30, false, 0, NULL, NULL, @schema_id),
    ('АУМН-600',        'LABEL', 490,  370, 100, 30, false, 0, NULL, NULL, @schema_id),
    ('АУ311-300',       'LABEL', 740,  140, 100, 30, false, 0, NULL, NULL, @schema_id),
    ('Наддув',          'LABEL',1050,  140, 80,  30, false, 0, NULL, NULL, @schema_id),
    ('Заполнение',      'LABEL',1050,  370, 80,  30, false, 0, NULL, NULL, @schema_id),
    ('Дренаж',          'LABEL',1250,  140, 80,  30, false, 0, NULL, NULL, @schema_id),

    -- === Входной участок ===
    -- Фильтр воздуха Д060-060 на входе магистрали
    ('FL1',  'FILTER',            80, 250, 60, 60, false, 0, NULL, NULL, @schema_id),
    -- Вентиль запорный ВД (АУ341-200, до 230 ати)
    ('VP1',  'VALVE',            220, 250, 60, 60, false, 0, NULL, NULL, @schema_id),
    -- Манометр ВД (МВ1-300, диапазон 0-300 кг/см²)
    ('PT1',  'SENSOR_PRESSURE',  360, 250, 60, 60, false, 0, 0, 300, @schema_id),

    -- === Редукция высокого давления ===
    -- Редуктор АУМО-400: 230±70 → 55±20 ати
    ('RD1',  'REDUCER',          520, 250, 60, 60, false, 0, NULL, NULL, @schema_id),
    -- Предохранительный клапан АУМН-600 (срабатывание 35-65 ати)
    ('SV1',  'SAFETY_VALVE',     520, 430, 60, 60, false, 0, NULL, NULL, @schema_id),
    -- Манометр после редуктора (МВ1-100, диапазон 0-100 кг/см²)
    ('PT2',  'SENSOR_PRESSURE',  660, 250, 60, 60, false, 0, 0, 100, @schema_id),

    -- === Редукция низкого давления ===
    -- Редуктор АУ311-300: 50±5 → давление наддува
    ('RD2',  'REDUCER',          770, 250, 60, 60, false, 0, NULL, NULL, @schema_id),
    -- Манометр НД (диапазон 0-60 кг/см²)
    ('PT3',  'SENSOR_PRESSURE',  910, 250, 60, 60, false, 0, 0, 60, @schema_id),

    -- === Распределение ===
    -- Вентиль линии наддува (АУ313-000)
    ('VP2',  'VALVE',           1080, 250, 60, 60, false, 0, NULL, NULL, @schema_id),
    -- Вентиль линии заполнения баллонов (АУ313-100)
    ('VP3',  'VALVE',           1080, 430, 60, 60, false, 0, NULL, NULL, @schema_id),

    -- === Дренажная линия ===
    -- Вентиль дренажа (АУ341-200)
    ('VP4',  'VALVE',           1280, 250, 60, 60, false, 0, NULL, NULL, @schema_id),
    -- Обратный клапан дренажа (сброс в атмосферу)
    ('CV1',  'CHECK_VALVE',     1400, 250, 60, 60, false, 0, NULL, NULL, @schema_id);

-- ====== Получение id элементов ======
SET @fl1 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'FL1');
SET @vp1 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'VP1');
SET @pt1 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'PT1');
SET @rd1 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'RD1');
SET @sv1 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'SV1');
SET @pt2 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'PT2');
SET @rd2 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'RD2');
SET @pt3 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'PT3');
SET @vp2 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'VP2');
SET @vp3 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'VP3');
SET @vp4 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'VP4');
SET @cv1 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'CV1');

-- ====== Соединения (трубопроводы) ======
-- Основная магистраль: FL1 → VP1 → PT1 → RD1 → PT2 → RD2 → PT3 → VP2
INSERT INTO t_schema_connection (schema_id, source_element_id, target_element_id, path_data)
VALUES
    (@schema_id, @fl1, @vp1, NULL),
    (@schema_id, @vp1, @pt1, NULL),
    (@schema_id, @pt1, @rd1, NULL),
    (@schema_id, @rd1, @pt2, NULL),
    (@schema_id, @pt2, @rd2, NULL),
    (@schema_id, @rd2, @pt3, NULL),
    (@schema_id, @pt3, @vp2, NULL),

    -- Ответвление на заполнение: PT3 → VP3
    (@schema_id, @pt3, @vp3, NULL),

    -- Предохранительный клапан: RD1 → SV1 (сброс избыточного давления)
    (@schema_id, @rd1, @sv1, NULL),

    -- Дренажная линия: VP2 → VP4 → CV1
    (@schema_id, @vp2, @vp4, NULL),
    (@schema_id, @vp4, @cv1, NULL);

-- ====== ШТАТНЫЙ СЦЕНАРИЙ ======
-- На основе раздела V документации: «Подготовка к эксплуатации»
INSERT INTO t_simulation_scenario
    (title, description, time_limit, is_active, schema_id, created_by_id, scenario_type, parent_scenario_id)
VALUES (
    @scenario_title,
    'Последовательная подготовка пневмощитка 8У016 к работе по инструкции: подача давления, настройка редукторов, открытие линий распределения.',
    10,
    true,
    @schema_id,
    @created_by_id,
    'NORMAL',
    NULL
);

SET @scenario_id := LAST_INSERT_ID();

INSERT INTO t_scenario_step (step_number, instruction_text, expected_state, scenario_id)
VALUES
    (1,
     'Откройте входной вентиль VP1 для подачи высокого давления (230 ати) в магистраль. Убедитесь, что манометр PT1 показывает давление.',
     '{"VP1": true}',
     @scenario_id),

    (2,
     'Включите редуктор высокого давления RD1 (АУМО-400) для снижения давления с 230 до 55 ати. Проверьте показания манометра PT2.',
     '{"RD1": true}',
     @scenario_id),

    (3,
     'Включите редуктор низкого давления RD2 (АУ311-300) для дальнейшего снижения давления до рабочего уровня. Проверьте показания PT3.',
     '{"RD2": true}',
     @scenario_id),

    (4,
     'Откройте вентиль VP2 линии наддува для подачи воздуха в систему.',
     '{"VP2": true}',
     @scenario_id),

    (5,
     'Откройте вентиль VP3 линии заполнения баллонов. Система готова к работе.',
     '{"VP3": true}',
     @scenario_id);

INSERT INTO t_scenario_department (scenario_id, department_id)
VALUES (@scenario_id, @department_id);

-- ====== АВАРИЙНЫЙ СЦЕНАРИЙ ======
-- Ситуация: после включения редуктора АУМО-400 давление на выходе превышает норму.
-- Необходимо стравить давление через дренаж, а не продолжать работу.
INSERT INTO t_simulation_scenario
    (title, description, time_limit, is_active, schema_id, created_by_id, scenario_type, parent_scenario_id)
VALUES (
    @fault_title,
    'Аварийная ситуация: давление на выходе редуктора АУМО-400 превышает допустимое значение. Необходимо стравить давление через дренажную линию.',
    12,
    true,
    @schema_id,
    @created_by_id,
    'FAULT',
    @scenario_id
);

SET @fault_id := LAST_INSERT_ID();

INSERT INTO t_scenario_step
    (step_number, instruction_text, expected_state, fault_event, forbidden_actions, step_time_limit, scenario_id)
VALUES
    -- Шаг 1: штатный, как в нормальном сценарии
    (1,
     'Откройте входной вентиль VP1 для подачи высокого давления в магистраль.',
     '{"VP1": true}',
     NULL, NULL, NULL,
     @fault_id),

    -- Шаг 2: штатный — включаем редуктор
    (2,
     'Включите редуктор высокого давления RD1 (АУМО-400).',
     '{"RD1": true}',
     NULL, NULL, NULL,
     @fault_id),

    -- Шаг 3: АВАРИЯ — давление на PT2 превышает норму
    -- Нужно открыть дренажный вентиль VP4, а НЕ продолжать работу.
    -- Запрещено открывать VP2 (наддув) при аномальном давлении — это приведёт к аварии.
    (3,
     'АВАРИЯ: давление на выходе редуктора (PT2) превышает допустимое! Откройте дренажный вентиль VP4 для стравливания избыточного давления. НЕ ОТКРЫВАЙТЕ вентиль наддува VP2!',
     '{"VP4": true}',
     '{"type":"PRESSURE_ANOMALY","elementName":"PT2","message":"Давление на выходе АУМО-400 превышает 75 ати! Необходимо стравить давление через дренаж.","lockElement":false,"sensorOverrides":{"PT2":75.0}}',
     '[{"elementName":"VP2","action":"on","penalty":"FAIL","message":"Подача воздуха при аномальном давлении запрещена! Опасность разрушения магистрали."}]',
     60,
     @fault_id),

    -- Шаг 4: после стравливания — продолжить штатную работу
    (4,
     'Давление нормализовалось. Включите редуктор низкого давления RD2 и откройте вентиль VP2 линии наддува.',
     '{"RD2": true, "VP2": true}',
     NULL, NULL, NULL,
     @fault_id),

    -- Шаг 5: завершение
    (5,
     'Откройте вентиль VP3 линии заполнения баллонов. Система готова к работе.',
     '{"VP3": true}',
     NULL, NULL, NULL,
     @fault_id);

INSERT INTO t_scenario_department (scenario_id, department_id)
VALUES (@fault_id, @department_id);

COMMIT;

-- ====== Проверка ======
SELECT 'schema' AS entity, id, title FROM t_mnemo_schema WHERE id = @schema_id
UNION ALL
SELECT 'сценарий (штатный)', id, title FROM t_simulation_scenario WHERE id = @scenario_id
UNION ALL
SELECT 'сценарий (аварийный)', id, title FROM t_simulation_scenario WHERE id = @fault_id;

SELECT CONCAT(COUNT(*), ' элементов') AS info FROM t_schema_element WHERE schema_id = @schema_id
UNION ALL
SELECT CONCAT(COUNT(*), ' соединений') FROM t_schema_connection WHERE schema_id = @schema_id
UNION ALL
SELECT CONCAT(COUNT(*), ' шагов (штатный)') FROM t_scenario_step WHERE scenario_id = @scenario_id
UNION ALL
SELECT CONCAT(COUNT(*), ' шагов (аварийный)') FROM t_scenario_step WHERE scenario_id = @fault_id;
