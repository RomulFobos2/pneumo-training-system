ALTER TABLE t_testQuestion
    ADD COLUMN difficulty_level INT NOT NULL DEFAULT 1;

UPDATE t_testQuestion
SET difficulty_level = 1
WHERE difficulty_level IS NULL OR difficulty_level NOT BETWEEN 1 AND 3;

ALTER TABLE t_testSessionAnswer
    ADD COLUMN earned_score_ratio DOUBLE NULL;
