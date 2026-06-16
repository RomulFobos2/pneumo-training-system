-- MySQL 8+
-- Одноразовая миграция: добавить колонку questions_per_attempt в t_test
-- и проставить её существующим тестам = текущему числу вопросов
-- (чтобы поведение тестов не изменилось — выдаются все имеющиеся вопросы).

SET NAMES utf8mb4;
START TRANSACTION;

-- 1) Добавить колонку, если её ещё нет
SET @col_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_test'
      AND COLUMN_NAME = 'questions_per_attempt'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE t_test ADD COLUMN questions_per_attempt INT NOT NULL DEFAULT 1',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) Заполнить значение по количеству существующих вопросов теста
UPDATE t_test t
SET t.questions_per_attempt = GREATEST(1, COALESCE(
    (SELECT COUNT(*) FROM t_test_question q WHERE q.test_id = t.id), 1
));

COMMIT;

SELECT id, title, questions_per_attempt,
       (SELECT COUNT(*) FROM t_test_question q WHERE q.test_id = t.id) AS total_questions
FROM t_test t
ORDER BY id;
