-- Добавление колонок min_value и max_value для настраиваемых диапазонов датчиков
ALTER TABLE t_schema_element ADD COLUMN min_value DOUBLE NULL;
ALTER TABLE t_schema_element ADD COLUMN max_value DOUBLE NULL;
