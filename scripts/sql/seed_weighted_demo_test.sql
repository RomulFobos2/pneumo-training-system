-- MySQL 8+
-- Демонстрационный тест для проверки новой взвешенной системы оценивания.
-- Тест доступен только по назначению, но разрешён для всех подразделений.
--
-- Что демонстрирует тест:
-- 1) У вопросов разные уровни сложности difficulty_level.
-- 2) Итог считается по формуле R = (Σ(r_i * z_i) / Σ z_i) * 100.
-- 3) MULTIPLE_CHOICE даёт частичный результат по формуле:
--    r_i = max(0, correctSelected - wrongSelected) / correctTotal
-- 4) MATCHING даёт долю правильных пар.
-- 5) SEQUENCE даёт долю элементов на правильных позициях.
--
-- Пример демонстрации:
-- - Q1 (SINGLE_CHOICE, сложность 1) -> неверно -> r = 0.00
-- - Q2 (SINGLE_CHOICE, сложность 1) -> верно   -> r = 1.00
-- - Q3 (MULTIPLE_CHOICE, сложность 2) -> выбран только один правильный ответ из двух -> r = 0.50
-- - Q4 (MATCHING, сложность 2) -> 2 пары из 4 верны -> r = 0.50
-- - Q5 (SEQUENCE, сложность 3) -> верно полностью -> r = 1.00
--
-- Тогда:
-- Σ(r_i * z_i) = 0*1 + 1*1 + 0.5*2 + 0.5*2 + 1*3 = 6
-- Σ(z_i) = 1 + 1 + 2 + 2 + 3 = 9
-- R = 6 / 9 * 100 = 66.7%
--
-- При passing_score = 60 такой сценарий даст зачёт,
-- даже если один лёгкий вопрос решён неверно, а сложный — верно.

SET NAMES utf8mb4;
START TRANSACTION;

SET @chief_username := CONVERT('admin' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci;
SET @test_title := CONVERT('Демонстрация взвешенного оценивания' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci;
SET @test_description := CONVERT(
    'Тест для проверки новой модели оценивания с коэффициентом ответа и уровнем сложности вопроса.' USING utf8mb4
) COLLATE utf8mb4_0900_ai_ci;
SET @deadline_days := 30;

SET @created_by_id := (
    SELECT e.id
    FROM t_employee e
    WHERE e.username COLLATE utf8mb4_general_ci = @chief_username COLLATE utf8mb4_general_ci
    LIMIT 1
);

SET @created_by_id := COALESCE(
    @created_by_id,
    (
        SELECT e2.id
        FROM t_employee e2
        JOIN t_role r2 ON r2.id = e2.role_id
        WHERE r2.name IN ('ROLE_EMPLOYEE_CHIEF', 'CHIEF')
          AND e2.is_active = TRUE
        ORDER BY e2.id
        LIMIT 1
    ),
    (
        SELECT e3.id
        FROM t_employee e3
        JOIN t_role r3 ON r3.id = e3.role_id
        WHERE r3.name IN ('ROLE_EMPLOYEE_ADMIN', 'ADMIN')
          AND e3.is_active = TRUE
        ORDER BY e3.id
        LIMIT 1
    ),
    1
);

-- ===== Очистка старой демонстрации для повторного запуска =====
DELETE s_sel
FROM t_test_session_answer_selected_answers s_sel
JOIN t_test_session_answer s_ans ON s_ans.id = s_sel.session_answer_id
JOIN t_test_session s ON s.id = s_ans.test_session_id
JOIN t_test t ON t.id = s.test_id
WHERE t.title COLLATE utf8mb4_general_ci = @test_title COLLATE utf8mb4_general_ci;

DELETE s_ans
FROM t_test_session_answer s_ans
JOIN t_test_session s ON s.id = s_ans.test_session_id
JOIN t_test t ON t.id = s.test_id
WHERE t.title COLLATE utf8mb4_general_ci = @test_title COLLATE utf8mb4_general_ci;

DELETE s
FROM t_test_session s
JOIN t_test t ON t.id = s.test_id
WHERE t.title COLLATE utf8mb4_general_ci = @test_title COLLATE utf8mb4_general_ci;

DELETE tae
FROM t_test_assignment_employee tae
JOIN t_test_assignment ta ON ta.id = tae.assignment_id
JOIN t_test t ON t.id = ta.test_id
WHERE t.title COLLATE utf8mb4_general_ci = @test_title COLLATE utf8mb4_general_ci;

DELETE ta
FROM t_test_assignment ta
JOIN t_test t ON t.id = ta.test_id
WHERE t.title COLLATE utf8mb4_general_ci = @test_title COLLATE utf8mb4_general_ci;

DELETE a
FROM t_test_answer a
JOIN t_test_question q ON q.id = a.question_id
JOIN t_test t ON t.id = q.test_id
WHERE t.title COLLATE utf8mb4_general_ci = @test_title COLLATE utf8mb4_general_ci;

DELETE q
FROM t_test_question q
JOIN t_test t ON t.id = q.test_id
WHERE t.title COLLATE utf8mb4_general_ci = @test_title COLLATE utf8mb4_general_ci;

DELETE td
FROM t_test_department td
JOIN t_test t ON t.id = td.test_id
WHERE t.title COLLATE utf8mb4_general_ci = @test_title COLLATE utf8mb4_general_ci;

DELETE FROM t_test
WHERE title COLLATE utf8mb4_general_ci = @test_title COLLATE utf8mb4_general_ci;

-- ===== Создание теста =====
INSERT INTO t_test (
    title,
    description,
    time_limit,
    passing_score,
    available_without_assignment,
    allow_back_navigation,
    created_by_id
)
VALUES (
    @test_title,
    @test_description,
    20,
    60,
    FALSE,
    TRUE,
    @created_by_id
);

SET @test_id := LAST_INSERT_ID();

-- Тест разрешён для всех подразделений, но доступен только по назначению,
-- потому что available_without_assignment = FALSE.
INSERT INTO t_test_department (test_id, department_id)
SELECT @test_id, d.id
FROM t_department d;

-- ===== Вопрос 1: SINGLE_CHOICE, сложность 1 =====
INSERT INTO t_test_question (question_text, difficulty_level, question_type, theory_section_id, test_id)
VALUES ('Вопрос 1. Выберите один.', 1, 'SINGLE_CHOICE', NULL, @test_id);
SET @q1_id := LAST_INSERT_ID();

INSERT INTO t_test_answer (answer_text, is_correct, sort_order, match_target, question_id) VALUES
('Один',  FALSE, 1, NULL, @q1_id),
('Два',   TRUE,  2, NULL, @q1_id),
('Три',   FALSE, 3, NULL, @q1_id),
('Четыре',FALSE, 4, NULL, @q1_id);

-- ===== Вопрос 2: SINGLE_CHOICE, сложность 1 =====
INSERT INTO t_test_question (question_text, difficulty_level, question_type, theory_section_id, test_id)
VALUES ('Вопрос 2. Выберите один.', 1, 'SINGLE_CHOICE', NULL, @test_id);
SET @q2_id := LAST_INSERT_ID();

INSERT INTO t_test_answer (answer_text, is_correct, sort_order, match_target, question_id) VALUES
('Один',   FALSE, 1, NULL, @q2_id),
('Два',    FALSE, 2, NULL, @q2_id),
('Три',    TRUE,  3, NULL, @q2_id),
('Четыре', FALSE, 4, NULL, @q2_id);

-- ===== Вопрос 3: MULTIPLE_CHOICE, сложность 2 =====
INSERT INTO t_test_question (question_text, difficulty_level, question_type, theory_section_id, test_id)
VALUES ('Вопрос 3. Выберите несколько вариантов.', 2, 'MULTIPLE_CHOICE', NULL, @test_id);
SET @q3_id := LAST_INSERT_ID();

INSERT INTO t_test_answer (answer_text, is_correct, sort_order, match_target, question_id) VALUES
('Один',   TRUE,  1, NULL, @q3_id),
('Два',    TRUE,  2, NULL, @q3_id),
('Три',    FALSE, 3, NULL, @q3_id),
('Четыре', FALSE, 4, NULL, @q3_id);

-- ===== Вопрос 4: MATCHING, сложность 2 =====
INSERT INTO t_test_question (question_text, difficulty_level, question_type, theory_section_id, test_id)
VALUES ('Вопрос 4. Установите соответствие.', 2, 'MATCHING', NULL, @test_id);
SET @q4_id := LAST_INSERT_ID();

INSERT INTO t_test_answer (answer_text, is_correct, sort_order, match_target, question_id) VALUES
('Один',   FALSE, 1, 'А', @q4_id),
('Два',    FALSE, 2, 'Б', @q4_id),
('Три',    FALSE, 3, 'В', @q4_id),
('Четыре', FALSE, 4, 'Г', @q4_id);

-- ===== Вопрос 5: SEQUENCE, сложность 3 =====
INSERT INTO t_test_question (question_text, difficulty_level, question_type, theory_section_id, test_id)
VALUES ('Вопрос 5. Установите правильную последовательность.', 3, 'SEQUENCE', NULL, @test_id);
SET @q5_id := LAST_INSERT_ID();

INSERT INTO t_test_answer (answer_text, is_correct, sort_order, match_target, question_id) VALUES
('Один',   FALSE, 1, NULL, @q5_id),
('Два',    FALSE, 2, NULL, @q5_id),
('Три',    FALSE, 3, NULL, @q5_id),
('Четыре', FALSE, 4, NULL, @q5_id);

-- ===== Создание назначения =====
INSERT INTO t_test_assignment (test_id, deadline, created_by_id, created_at)
VALUES (@test_id, DATE_ADD(CURDATE(), INTERVAL @deadline_days DAY), @created_by_id, NOW());

SET @assignment_id := LAST_INSERT_ID();

INSERT INTO t_test_assignment_employee (assignment_id, employee_id, status, completed_session_id)
SELECT @assignment_id, e.id, 'PENDING', NULL
FROM t_employee e
WHERE e.is_active = TRUE
  AND e.department_id IS NOT NULL;

COMMIT;

-- После загрузки можно проверять сценарии в UI.
-- Рекомендуемый демонстрационный сценарий для зачёта при ошибке на лёгком вопросе:
-- Q1: неверно
-- Q2: верно
-- Q3: выбрать только "Один"
-- Q4: сопоставить Один->А, Два->Б, Три->Г, Четыре->В
-- Q5: установить порядок Один, Два, Три, Четыре
