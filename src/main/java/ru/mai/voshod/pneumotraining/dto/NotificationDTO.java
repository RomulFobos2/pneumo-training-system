package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationDTO {
    private Long id;
    private String message;
    private String link;
    private boolean isRead;
    private LocalDateTime createdAt;
}
