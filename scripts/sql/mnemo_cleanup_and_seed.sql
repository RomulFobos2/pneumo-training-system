-- MySQL 8+
-- Очистка и создание учебной мнемосхемы со сценарием.
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

-- Сначала аварийные (дочерние), потом штатные (родительские) — FK parent_scenario_id
DELETE sc
FROM t_simulation_scenario sc
JOIN t_mnemo_schema ms ON ms.id = sc.schema_id
WHERE (ms.title = @schema_title OR sc.title = @scenario_title)
  AND sc.parent_scenario_id IS NOT NULL;

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
    (title, description, time_limit, is_active, schema_id, created_by_id, scenario_type, parent_scenario_id)
VALUES
    (
        @scenario_title,
        'Последовательный запуск пневмосистемы ДУ: подготовка входного контура, запуск основной магистрали, включение контура нагрева, открытие выходных клапанов.',
        10,
        true,
        @schema_id,
        @created_by_id,
        'NORMAL',
        NULL
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
     '{"VP2": true}',
     @scenario_id),

    (3,
     'Включите переключатель WS1 для активации распределения потока. Проверьте давление на PT2.',
     '{"WS1": true}',
     @scenario_id),

    (4,
     'Откройте клапан VP5 контура нагрева, включите нагреватель NR1 и проверьте температуру на TT1.',
     '{"VP5": true, "NR1": true}',
     @scenario_id),

    (5,
     'Откройте выходные клапаны VP3, VP4 и VP6 для завершения запуска системы.',
     '{"VP3": true, "VP4": true, "VP6": true}',
     @scenario_id);

-- Доступ сценария для подразделения
INSERT INTO t_scenario_department (scenario_id, department_id)
VALUES (@scenario_id, @department_id);

-- ====== Создание АВАРИЙНОГО сценария ======
-- Демонстрирует все типы аварийных событий: ELEMENT_FAILURE, PRESSURE_ANOMALY,
-- OVERHEAT, FALSE_ALARM, а также forbiddenActions и stepTimeLimit.
SET @fault_scenario_title := CONVERT('Аварийный запуск пневмосистемы' USING utf8mb4) COLLATE utf8mb4_general_ci;

-- Очистка предыдущих данных аварийного сценария (если повторный запуск)
DELETE ss2
FROM t_simulation_session ss2
JOIN t_simulation_scenario sc2 ON sc2.id = ss2.scenario_id
WHERE sc2.title = @fault_scenario_title AND sc2.schema_id = @schema_id;

DELETE sd2
FROM t_scenario_department sd2
JOIN t_simulation_scenario sc2 ON sc2.id = sd2.scenario_id
WHERE sc2.title = @fault_scenario_title AND sc2.schema_id = @schema_id;

DELETE st2
FROM t_scenario_step st2
JOIN t_simulation_scenario sc2 ON sc2.id = st2.scenario_id
WHERE sc2.title = @fault_scenario_title AND sc2.schema_id = @schema_id;

DELETE FROM t_simulation_scenario
WHERE title = @fault_scenario_title AND schema_id = @schema_id;

INSERT INTO t_simulation_scenario
    (title, description, time_limit, is_active, schema_id, created_by_id, scenario_type, parent_scenario_id)
VALUES
    (
        @fault_scenario_title,
        'Демонстрационный сценарий с аварийными ситуациями: отказ элемента, аномалия давления, перегрев, ложная тревога, запрещённые действия и ограничение времени на шаг.',
        15,
        true,
        @schema_id,
        @created_by_id,
        'FAULT',
        @scenario_id
    );

SET @fault_scenario_id := LAST_INSERT_ID();

-- ====== Шаги аварийного сценария ======
INSERT INTO t_scenario_step
    (step_number, instruction_text, expected_state, fault_event, forbidden_actions, step_time_limit, scenario_id)
VALUES
    -- ШАГ 1: Обычный старт (без аварий) — открыть VP1 и запустить N1
    (1,
     'Откройте входной клапан VP1 и запустите насос N1. Это штатный шаг без аварий.',
     '{"VP1": true, "N1": true}',
     NULL, NULL, NULL,
     @fault_scenario_id),

    -- ШАГ 2: ELEMENT_FAILURE — отказ клапана VP5 (заблокирован)
    -- Специалист должен обойти VP5 и открыть только VP2.
    -- Попытка включить VP5 → «Элемент неисправен» (без провала, просто заблокирован).
    (2,
     'АВАРИЯ: клапан VP5 вышел из строя! Откройте клапан VP2 для подачи в основную магистраль. НЕ ТРОГАЙТЕ VP5 — он заблокирован.',
     '{"VP2": true}',
     '{"type":"ELEMENT_FAILURE","elementName":"VP5","message":"Отказ клапана VP5! Клапан заблокирован, обойдите его.","lockElement":true}',
     NULL, NULL,
     @fault_scenario_id),

    -- ШАГ 3: PRESSURE_ANOMALY на PT1 + запрещённое действие (FAIL)
    -- Датчик PT1 показывает аномалию. Нужно включить WS1 для перераспределения.
    -- Запрещено выключать насос N1 — это приведёт к гидроудару (штраф FAIL).
    (3,
     'АВАРИЯ: аномалия давления на PT1! Включите переключатель WS1 для перераспределения потока. ЗАПРЕЩЕНО выключать насос N1 — это вызовет гидроудар!',
     '{"WS1": true}',
     '{"type":"PRESSURE_ANOMALY","elementName":"PT1","message":"Аномальное давление на датчике PT1! Необходимо перераспределить поток.","lockElement":false}',
     '[{"elementName":"N1","action":"off","penalty":"FAIL","message":"Гидроудар! Выключение насоса при аномальном давлении запрещено."}]',
     NULL,
     @fault_scenario_id),

    -- ШАГ 4: OVERHEAT на NR1 + ограничение времени 90 сек + запрещённое действие (WARNING)
    -- Нагреватель перегрелся. Нужно открыть VP3 для отвода тепла.
    -- Запрещено включать нагреватель NR1 — предупреждение (WARNING, не провал).
    -- На шаг дано 90 секунд.
    (4,
     'АВАРИЯ: перегрев нагревателя NR1! Откройте клапан VP3 для аварийного отвода тепла. НЕ ВКЛЮЧАЙТЕ нагреватель NR1. Время ограничено — 90 секунд!',
     '{"VP3": true}',
     '{"type":"OVERHEAT","elementName":"NR1","message":"Перегрев нагревателя NR1! Необходим аварийный отвод тепла.","lockElement":false}',
     '[{"elementName":"NR1","action":"on","penalty":"WARNING","message":"Внимание! Включение перегретого нагревателя опасно."}]',
     90,
     @fault_scenario_id),

    -- ШАГ 5: FALSE_ALARM на BH1 + запрещённое действие (TIME_PENALTY)
    -- Ложное срабатывание блокировки BH1. Нужно открыть VP4 и VP6, завершив систему.
    -- Запрещено включать BH1 (аварийную блокировку) — штраф времени.
    (5,
     'ЛОЖНАЯ ТРЕВОГА: сработала аварийная блокировка BH1, но это ложное срабатывание. Откройте выходные клапаны VP4 и VP6 для завершения запуска. НЕ АКТИВИРУЙТЕ BH1 — это ложная тревога, активация приведёт к штрафу времени.',
     '{"VP4": true, "VP6": true}',
     '{"type":"FALSE_ALARM","elementName":"BH1","message":"Ложное срабатывание аварийной блокировки BH1! Игнорируйте — система работает штатно.","lockElement":false}',
     '[{"elementName":"BH1","action":"on","penalty":"TIME_PENALTY","message":"Активация BH1 при ложной тревоге — штраф 30 секунд!"}]',
     NULL,
     @fault_scenario_id);

-- Доступ аварийного сценария для подразделения
INSERT INTO t_scenario_department (scenario_id, department_id)
VALUES (@fault_scenario_id, @department_id);

COMMIT;

-- ====== Проверка результата ======
SELECT 'schema' AS entity, id, title FROM t_mnemo_schema WHERE id = @schema_id
UNION ALL
SELECT 'scenario (штатный)', id, title FROM t_simulation_scenario WHERE id = @scenario_id
UNION ALL
SELECT 'scenario (аварийный)', id, title FROM t_simulation_scenario WHERE id = @fault_scenario_id;

SELECT CONCAT(COUNT(*), ' elements') AS info FROM t_schema_element WHERE schema_id = @schema_id
UNION ALL
SELECT CONCAT(COUNT(*), ' connections') FROM t_schema_connection WHERE schema_id = @schema_id
UNION ALL
SELECT CONCAT(COUNT(*), ' steps (штатный)') FROM t_scenario_step WHERE scenario_id = @scenario_id
UNION ALL
SELECT CONCAT(COUNT(*), ' steps (аварийный)') FROM t_scenario_step WHERE scenario_id = @fault_scenario_id;
