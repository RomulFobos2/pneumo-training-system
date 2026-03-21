-- MySQL 8+
-- Очистка и создание учебной мнемосхемы со сценарием.
-- Таблицы в MySQL: t_mnemo_schema, t_schema_element, t_schema_connection,
-- t_simulation_scenario, t_scenario_step, t_scenario_department, t_simulation_session.
-- expected_state использует ИМЕНА элементов (например "VP1"), не numeric id.

SET NAMES utf8mb4;

START TRANSACTION;

-- ====== Параметры ======
-- created_by_id = id пользователя с ролью CHIEF (начальник группы)
-- department_id = id подразделения, которому доступен сценарий
SET @created_by_id   := 1;
SET @department_id   := 3;
SET @schema_title    := CONVERT('Пневмосистема ДУ (учебная)' USING utf8mb4) COLLATE utf8mb4_general_ci;
SET @scenario_title  := CONVERT('Запуск пневмосистемы' USING utf8mb4) COLLATE utf8mb4_general_ci;

-- ====== Очистка связанных данных ======
DELETE ss
FROM t_simulation_session ss
JOIN t_simulation_scenario sc ON sc.id = ss.scenario_id
JOIN t_mnemo_schema ms ON ms.id = sc.schema_id
WHERE ms.title = @schema_title
   OR sc.title = @scenario_title;

DELETE sd
FROM t_scenario_department sd
JOIN t_simulation_scenario sc ON sc.id = sd.scenario_id
JOIN t_mnemo_schema ms ON ms.id = sc.schema_id
WHERE ms.title = @schema_title
   OR sc.title = @scenario_title;

DELETE st
FROM t_scenario_step st
JOIN t_simulation_scenario sc ON sc.id = st.scenario_id
JOIN t_mnemo_schema ms ON ms.id = sc.schema_id
WHERE ms.title = @schema_title
   OR sc.title = @scenario_title;

DELETE sc
FROM t_simulation_scenario sc
JOIN t_mnemo_schema ms ON ms.id = sc.schema_id
WHERE ms.title = @schema_title
   OR sc.title = @scenario_title;

DELETE cn
FROM t_schema_connection cn
JOIN t_mnemo_schema ms ON ms.id = cn.schema_id
WHERE ms.title = @schema_title;

DELETE el
FROM t_schema_element el
JOIN t_mnemo_schema ms ON ms.id = el.schema_id
WHERE ms.title = @schema_title;

DELETE
FROM t_mnemo_schema
WHERE title = @schema_title;

-- ====== Создание схемы ======
-- Схема 1400x700 — упрощённая пневмосистема ДУ с тремя контурами:
-- вход → насос → распределение → датчики → выход + нагрев
INSERT INTO t_mnemo_schema (title, description, width, height, created_by_id)
VALUES (
    @schema_title,
    'Упрощённая учебная схема пневмоиспытаний ДУ РКН. Три контура: входной, распределительный, выходной.',
    1400,
    700,
    @created_by_id
);

SET @schema_id := LAST_INSERT_ID();

-- ====== Элементы схемы ======
-- Разложены по трём контурам (верхний ряд — основная магистраль,
-- нижний ряд — контур нагрева/вакуумирования)
INSERT INTO t_schema_element
    (name, element_type, posx, posy, width, height, initial_state, rotation, schema_id)
VALUES
    -- Надписи (LABEL) — подписи участков
    ('Вход',           'LABEL',                40,  80, 80, 30, false, 0, @schema_id),
    ('Магистраль',     'LABEL',               420,  80, 100, 30, false, 0, @schema_id),
    ('Распределение',  'LABEL',               750,  80, 120, 30, false, 0, @schema_id),
    ('Выход',          'LABEL',              1100,  80, 80, 30, false, 0, @schema_id),
    ('Контур нагрева', 'LABEL',               420, 450, 120, 30, false, 0, @schema_id),

    -- Входной контур
    ('VP1',  'VALVE',              80, 180, 60, 60, false, 0, @schema_id),
    ('N1',   'PUMP',              230, 180, 60, 60, false, 0, @schema_id),
    ('PT1',  'SENSOR_PRESSURE',   380, 180, 60, 60, false, 0, @schema_id),

    -- Основная магистраль
    ('VP2',  'VALVE',             530, 180, 60, 60, false, 0, @schema_id),
    ('WS1',  'SWITCH',            680, 180, 60, 60, false, 0, @schema_id),
    ('PT2',  'SENSOR_PRESSURE',   830, 180, 60, 60, false, 0, @schema_id),

    -- Выходной контур
    ('VP3',  'VALVE',             980, 180, 60, 60, false, 0, @schema_id),
    ('VP4',  'VALVE',            1130, 180, 60, 60, false, 0, @schema_id),

    -- Контур нагрева (нижний ряд)
    ('VP5',  'VALVE',             530, 380, 60, 60, false, 0, @schema_id),
    ('NR1',  'HEATER',            680, 380, 60, 60, false, 0, @schema_id),
    ('TT1',  'SENSOR_TEMPERATURE',830, 380, 60, 60, false, 0, @schema_id),
    ('VP6',  'VALVE',             980, 380, 60, 60, false, 0, @schema_id),

    -- Блокировка аварийная
    ('BH1',  'LOCK',             1130, 380, 60, 60, false, 0, @schema_id);

-- Получаем id элементов
SET @vp1 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'VP1');
SET @n1  := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'N1');
SET @pt1 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'PT1');
SET @vp2 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'VP2');
SET @ws1 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'WS1');
SET @pt2 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'PT2');
SET @vp3 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'VP3');
SET @vp4 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'VP4');
SET @vp5 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'VP5');
SET @nr1 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'NR1');
SET @tt1 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'TT1');
SET @vp6 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'VP6');
SET @bh1 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'BH1');

-- ====== Соединения (трубопроводы) ======
-- Основная магистраль: VP1 → N1 → PT1 → VP2 → WS1 → PT2 → VP3 → VP4
INSERT INTO t_schema_connection
    (schema_id, source_element_id, target_element_id, path_data)
VALUES
    (@schema_id, @vp1, @n1,  NULL),
    (@schema_id, @n1,  @pt1, NULL),
    (@schema_id, @pt1, @vp2, NULL),
    (@schema_id, @vp2, @ws1, NULL),
    (@schema_id, @ws1, @pt2, NULL),
    (@schema_id, @pt2, @vp3, NULL),
    (@schema_id, @vp3, @vp4, NULL),

    -- Ответвление на контур нагрева: VP2 → VP5 → NR1 → TT1 → VP6
    (@schema_id, @vp2, @vp5, NULL),
    (@schema_id, @vp5, @nr1, NULL),
    (@schema_id, @nr1, @tt1, NULL),
    (@schema_id, @tt1, @vp6, NULL),

    -- Блокировка: VP4 → BH1 и VP6 → BH1
    (@schema_id, @vp4, @bh1, NULL),
    (@schema_id, @vp6, @bh1, NULL);

-- ====== Создание сценария ======
INSERT INTO t_simulation_scenario
    (title, description, time_limit, is_active, schema_id, created_by_id)
VALUES
    (
        @scenario_title,
        'Последовательный запуск пневмосистемы ДУ: подготовка входного контура, запуск основной магистрали, включение контура нагрева, открытие выходных клапанов.',
        10,
        true,
        @schema_id,
        @created_by_id
    );

SET @scenario_id := LAST_INSERT_ID();

-- ====== Шаги сценария ======
-- Каждый шаг содержит ТОЛЬКО НОВЫЕ изменения на данном шаге.
-- Backend при проверке строит полное ожидаемое состояние:
--   initialState (все false) + expectedState шагов 1..N кумулятивно.
-- Поэтому в expected_state шага достаточно указать только ИЗМЕНИВШИЕСЯ элементы.
INSERT INTO t_scenario_step
    (step_number, instruction_text, expected_state, scenario_id)
VALUES
    (1,
     'Откройте входной клапан VP1 и запустите насос N1 для подачи давления во входной контур.',
     '{"VP1": true, "N1": true}',
     @scenario_id),

    (2,
     'Проверьте показания датчика давления PT1 (должен показать давление). Откройте клапан VP2 для подачи в основную магистраль.',
     '{"PT1": true, "VP2": true}',
     @scenario_id),

    (3,
     'Включите переключатель WS1 для активации распределения потока. Проверьте давление на PT2.',
     '{"WS1": true, "PT2": true}',
     @scenario_id),

    (4,
     'Откройте клапан VP5 контура нагрева, включите нагреватель NR1 и проверьте температуру на TT1.',
     '{"VP5": true, "NR1": true, "TT1": true}',
     @scenario_id),

    (5,
     'Откройте выходные клапаны VP3, VP4 и VP6 для завершения запуска системы.',
     '{"VP3": true, "VP4": true, "VP6": true}',
     @scenario_id);

-- Доступ сценария для подразделения
INSERT INTO t_scenario_department (scenario_id, department_id)
VALUES (@scenario_id, @department_id);

COMMIT;

-- ====== Проверка результата ======
SELECT 'schema' AS entity, id, title FROM t_mnemo_schema WHERE id = @schema_id
UNION ALL
SELECT 'scenario', id, title FROM t_simulation_scenario WHERE id = @scenario_id;

SELECT CONCAT(COUNT(*), ' elements') AS info FROM t_schema_element WHERE schema_id = @schema_id
UNION ALL
SELECT CONCAT(COUNT(*), ' connections') FROM t_schema_connection WHERE schema_id = @schema_id
UNION ALL
SELECT CONCAT(COUNT(*), ' steps') FROM t_scenario_step WHERE scenario_id = @scenario_id;
