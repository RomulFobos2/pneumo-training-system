ALTER TABLE t_simulation_scenario
    ADD COLUMN available_without_assignment BIT(1) NOT NULL DEFAULT b'0';

UPDATE t_simulation_scenario
SET available_without_assignment = is_active;

ALTER TABLE t_simulation_scenario
    DROP COLUMN is_active;
