-- Добавление колонок min_value и max_value для настраиваемых диапазонов датчиков
-- Hibernate ddl-auto создаст их автоматически, но этот скрипт для ручного применения

ALTER TABLE t_schema_element ADD COLUMN min_value DOUBLE NULL;
ALTER TABLE t_schema_element ADD COLUMN max_value DOUBLE NULL;
