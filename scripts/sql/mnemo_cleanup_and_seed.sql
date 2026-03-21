-- MySQL 8+
-- Очистка и создание учебной мнемосхемы со сценарием.
-- ВАЖНО:
-- 1) В коде таблицы названы camelCase, но в MySQL фактически создаются как snake_case:
--    t_mnemo_schema, t_schema_element, t_schema_connection, ...
-- 2) expected_state/current_state в текущей реализации используют ИМЕНА элементов
--    (например "VP1"), а не их numeric id.

SET NAMES utf8mb4;

START TRANSACTION;

-- ====== Параметры ======
SET @created_by_id   := 1;
SET @department_id   := 3;
SET @schema_title    := CONVERT('Пневмосистема ДУ (учебная)' USING utf8mb4) COLLATE utf8mb4_general_ci;
SET @scenario_title  := CONVERT('Запуск пневмосистемы' USING utf8mb4) COLLATE utf8mb4_general_ci;

-- ====== Очистка связанных данных ======
-- Удаляем сначала сессии прохождения, потом связи сценария, потом сам сценарий,
-- затем соединения/элементы схемы и только после этого саму схему.

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
INSERT INTO t_mnemo_schema (title, description, width, height, created_by_id)
VALUES (
    @schema_title,
    'Упрощённая схема пневмоиспытаний двигательной установки',
    1200,
    800,
    @created_by_id
);

SET @schema_id := LAST_INSERT_ID();

-- Элементы схемы
INSERT INTO t_schema_element
    (name, element_type, posx, posy, width, height, initial_state, rotation, schema_id)
VALUES
    ('VP1', 'VALVE',           100, 350, 60, 60, false, 0, @schema_id),
    ('N1',  'PUMP',            250, 350, 60, 60, false, 0, @schema_id),
    ('VP2', 'VALVE',           400, 350, 60, 60, false, 0, @schema_id),
    ('PT1', 'SENSOR_PRESSURE', 550, 350, 60, 60, false, 0, @schema_id),
    ('VP3', 'VALVE',           700, 350, 60, 60, false, 0, @schema_id),
    ('H1',  'HEATER',          850, 350, 60, 60, false, 0, @schema_id);

SET @vp1 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'VP1');
SET @n1  := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'N1');
SET @vp2 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'VP2');
SET @pt1 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'PT1');
SET @vp3 := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'VP3');
SET @h1  := (SELECT id FROM t_schema_element WHERE schema_id = @schema_id AND name = 'H1');

-- Соединения
INSERT INTO t_schema_connection
    (schema_id, source_element_id, target_element_id, path_data)
VALUES
    (@schema_id, @vp1, @n1,  NULL),
    (@schema_id, @n1,  @vp2, NULL),
    (@schema_id, @vp2, @pt1, NULL),
    (@schema_id, @pt1, @vp3, NULL),
    (@schema_id, @vp3, @h1,  NULL);

-- ====== Создание сценария ======
INSERT INTO t_simulation_scenario
    (title, description, time_limit, is_active, schema_id, created_by_id)
VALUES
    (
        @scenario_title,
        'Последовательный запуск пневмосистемы ДУ: открытие клапанов, включение насоса, контроль давления, включение нагревателя.',
        5,
        true,
        @schema_id,
        @created_by_id
    );

SET @scenario_id := LAST_INSERT_ID();

-- Шаги сценария.
-- expected_state в текущем backend/frontend сравнивается по ИМЕНАМ элементов.
INSERT INTO t_scenario_step
    (step_number, instruction_text, expected_state, scenario_id)
VALUES
    (1, 'Откройте входной клапан VP1',
        '{"VP1": true}', @scenario_id),

    (2, 'Включите насос N1 для подачи давления',
        '{"VP1": true, "N1": true}', @scenario_id),

    (3, 'Откройте промежуточный клапан VP2 и проверьте показания датчика PT1',
        '{"VP1": true, "N1": true, "VP2": true}', @scenario_id),

    (4, 'Откройте выходной клапан VP3 и включите нагреватель H1',
        '{"VP1": true, "N1": true, "VP2": true, "VP3": true, "H1": true}', @scenario_id);

INSERT INTO t_scenario_department (scenario_id, department_id)
VALUES (@scenario_id, @department_id);

COMMIT;

-- ====== Проверка результата ======
SELECT 'schema' AS entity, id, title
FROM t_mnemo_schema
WHERE id = @schema_id

UNION ALL

SELECT 'scenario' AS entity, id, title
FROM t_simulation_scenario
WHERE id = @scenario_id;
