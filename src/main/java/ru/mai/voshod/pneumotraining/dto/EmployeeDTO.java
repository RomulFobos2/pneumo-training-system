package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EmployeeDTO {
    private Long id;
    private String lastName;
    private String firstName;
    private String patronymicName;
    private String fullName;
    private String username;
    private String roleName;
    private String roleDescription;
    private LocalDate dateOfRegistration;
    private boolean needChangePass;
    private boolean active;
    private String specialization;
    private String qualification;
    private BigDecimal salary;
    private String hiringOrderFile;
    private String dismissalOrderFile;
}
