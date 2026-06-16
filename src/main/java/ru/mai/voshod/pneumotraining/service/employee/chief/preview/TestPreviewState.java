package ru.mai.voshod.pneumotraining.service.employee.chief.preview;

import lombok.Data;
import lombok.NoArgsConstructor;
import ru.mai.voshod.pneumotraining.enumeration.TestSessionStatus;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory состояние пробного прогона теста для начальника группы.
 * Живёт в HttpSession и в БД не сохраняется.
 */
@Data
@NoArgsConstructor
public class TestPreviewState implements Serializable {

    private Long testId;
    private String testTitle;
    private Integer timeLimit;
    private Integer passingScore;
    private boolean allowBackNavigation;

    private LocalDateTime startedAt;
    private LocalDateTime endTime;
    private LocalDateTime finishedAt;

    private List<Long> questionOrder = new ArrayList<>();
    private Map<Long, TestPreviewAnswer> answers = new LinkedHashMap<>();

    private TestSessionStatus status = TestSessionStatus.IN_PROGRESS;
    private boolean finished;

    private Integer score;
    private Integer totalScore;
    private Double scorePercent;
    private Boolean isPassed;
}
