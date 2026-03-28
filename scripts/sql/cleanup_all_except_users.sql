-- MySQL 8+
-- Очистка ВСЕХ таблиц, кроме t_employee, t_role, t_department.
-- Порядок DELETE строго по FK-зависимостям (работает даже без FOREIGN_KEY_CHECKS=0).

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ====== 1. Уведомления ======
DELETE FROM t_notification;
ALTER TABLE t_notification AUTO_INCREMENT = 1;

-- ====== 2. Тесты: ответы сессий (join-таблица → t_test_session_answer, t_test_answer) ======
DELETE FROM t_test_session_answer_selected_answers;

-- ====== 3. Тесты: ответы сессий (→ t_test_session, t_test_question) ======
DELETE FROM t_test_session_answer;
ALTER TABLE t_test_session_answer AUTO_INCREMENT = 1;

-- ====== 4. Тесты: назначения сотрудников (→ t_test_assignment, t_test_session) ======
DELETE FROM t_test_assignment_employee;
ALTER TABLE t_test_assignment_employee AUTO_INCREMENT = 1;

-- ====== 5. Тесты: сессии (→ t_test, t_employee) ======
DELETE FROM t_test_session;
ALTER TABLE t_test_session AUTO_INCREMENT = 1;

-- ====== 6. Тесты: назначения (→ t_test, t_employee) ======
DELETE FROM t_test_assignment;
ALTER TABLE t_test_assignment AUTO_INCREMENT = 1;

-- ====== 7. Тесты: ответы вопросов (→ t_test_question) ======
DELETE FROM t_test_answer;
ALTER TABLE t_test_answer AUTO_INCREMENT = 1;

-- ====== 8. Тесты: вопросы (→ t_test) ======
DELETE FROM t_test_question;
ALTER TABLE t_test_question AUTO_INCREMENT = 1;

-- ====== 9. Тесты: привязка к отделам (→ t_test, t_department) ======
DELETE FROM t_test_department;

-- ====== 10. Тесты (→ t_employee) ======
DELETE FROM t_test;
ALTER TABLE t_test AUTO_INCREMENT = 1;

-- ====== 11. Теория: материалы (→ t_theory_section) ======
DELETE FROM t_theory_material;
ALTER TABLE t_theory_material AUTO_INCREMENT = 1;

-- ====== 12. Теория: разделы ======
DELETE FROM t_theory_section;
ALTER TABLE t_theory_section AUTO_INCREMENT = 1;

-- ====== 13. Симуляции: назначения сотрудников (→ t_simulation_assignment, t_simulation_session) ======
DELETE FROM t_simulation_assignment_employee;
ALTER TABLE t_simulation_assignment_employee AUTO_INCREMENT = 1;

-- ====== 14. Симуляции: сессии (→ t_simulation_scenario, t_employee) ======
DELETE FROM t_simulation_session;
ALTER TABLE t_simulation_session AUTO_INCREMENT = 1;

-- ====== 15. Симуляции: назначения (→ t_simulation_scenario, t_employee) ======
DELETE FROM t_simulation_assignment;
ALTER TABLE t_simulation_assignment AUTO_INCREMENT = 1;

-- ====== 16. Сценарии: привязка к отделам (→ t_simulation_scenario, t_department) ======
DELETE FROM t_scenario_department;

-- ====== 17. Сценарии: шаги (→ t_simulation_scenario) ======
DELETE FROM t_scenario_step;
ALTER TABLE t_scenario_step AUTO_INCREMENT = 1;

-- ====== 18. Сценарии: убрать self-reference перед удалением ======
UPDATE t_simulation_scenario SET parent_scenario_id = NULL WHERE parent_scenario_id IS NOT NULL;

-- ====== 19. Сценарии (→ t_mnemo_schema, t_employee, self-ref убран выше) ======
DELETE FROM t_simulation_scenario;
ALTER TABLE t_simulation_scenario AUTO_INCREMENT = 1;

-- ====== 20. Схемы: соединения (→ t_mnemo_schema) ======
DELETE FROM t_schema_connection;
ALTER TABLE t_schema_connection AUTO_INCREMENT = 1;

-- ====== 21. Схемы: элементы (→ t_mnemo_schema) ======
DELETE FROM t_schema_element;
ALTER TABLE t_schema_element AUTO_INCREMENT = 1;

-- ====== 22. Мнемосхемы (→ t_employee) ======
DELETE FROM t_mnemo_schema;
ALTER TABLE t_mnemo_schema AUTO_INCREMENT = 1;

SET FOREIGN_KEY_CHECKS = 1;

-- Готово. Таблицы t_employee, t_role, t_department не затронуты.
-- AUTO_INCREMENT сброшен на 1 для всех очищенных таблиц.
