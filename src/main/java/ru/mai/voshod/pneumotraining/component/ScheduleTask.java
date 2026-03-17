package ru.mai.voshod.pneumotraining.component;

import com.mai.siarsp.enumeration.EquipmentStatus;
import com.mai.siarsp.enumeration.WriteOffActStatus;
import com.mai.siarsp.enumeration.WriteOffReason;
import com.mai.siarsp.models.*;
import com.mai.siarsp.repo.*;
import com.mai.siarsp.service.employee.NotificationService;
import com.mai.siarsp.service.general.ProductExpirationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class ScheduleTask {

    private static final String MANAGER_ROLE = "ROLE_EMPLOYEE_MANAGER";
    private static final String ROLE_EMPLOYEE_WAREHOUSE_MANAGER = "ROLE_EMPLOYEE_WAREHOUSE_MANAGER";

    private final WarehouseEquipmentRepository warehouseEquipmentRepository;
    private final ProductRepository productRepository;
    private final ZoneProductRepository zoneProductRepository;
    private final WriteOffActRepository writeOffActRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;
    private final ProductExpirationService productExpirationService;

    public ScheduleTask(WarehouseEquipmentRepository warehouseEquipmentRepository,
                        ProductRepository productRepository,
                        ZoneProductRepository zoneProductRepository,
                        WriteOffActRepository writeOffActRepository,
                        EmployeeRepository employeeRepository,
                        NotificationService notificationService,
                        ProductExpirationService productExpirationService) {
        this.warehouseEquipmentRepository = warehouseEquipmentRepository;
        this.productRepository = productRepository;
        this.zoneProductRepository = zoneProductRepository;
        this.writeOffActRepository = writeOffActRepository;
        this.employeeRepository = employeeRepository;
        this.notificationService = notificationService;
        this.productExpirationService = productExpirationService;
    }

    @Bean
    public ApplicationRunner applicationRunner() {
        return args -> {
            checkEquipmentExpiration();
            checkProductExpiration();
        };
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanUpDirectories() {
    }

    @Scheduled(cron = "0 0 8 * * ?")
    public void checkEquipmentExpiration() {

        log.info("Запуск проверки сроков службы оборудования склада");

        List<WarehouseEquipment> activeEquipment =
                warehouseEquipmentRepository.findByStatusNot(EquipmentStatus.WRITTEN_OFF);

        LocalDate today = LocalDate.now();
        int notifiedCount = 0;

        for (WarehouseEquipment eq : activeEquipment) {
            LocalDate expDate = eq.getExpirationDate();
            if (expDate == null) {
                continue;
            }

            long daysLeft = ChronoUnit.DAYS.between(today, expDate);
            String warehouseName = eq.getWarehouse() != null ? eq.getWarehouse().getName() : "—";
            String text = null;

            if (daysLeft < 0) {
                text = "⚠️ Срок службы истёк: «" + eq.getName() + "» (склад: " + warehouseName
                        + "). Дата окончания: " + expDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + ". Статус: "
                        + eq.getStatus().getDisplayName() + ".";
            } else if (daysLeft <= 7) {
                text = "⚠️ Срок службы заканчивается через " + daysLeft + " дн.: «"
                        + eq.getName() + "» (склад: " + warehouseName + ", до " + expDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + ").";
            } else if (daysLeft <= 30) {
                text = "ℹ️ Срок службы заканчивается через месяц (" + daysLeft + " дн.): «"
                        + eq.getName() + "» (склад: " + warehouseName + ", до " + expDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + ").";
            }

            if (text != null) {
                notificationService.notifyByRole(MANAGER_ROLE, text);
                notificationService.notifyByRole(ROLE_EMPLOYEE_WAREHOUSE_MANAGER, text);
                notifiedCount++;
            }
        }

        log.info("Проверка сроков завершена. Отправлено уведомлений по {} единицам оборудования", notifiedCount);
    }

    /**
     * Проверка сроков годности товаров.
     * Выполняется при старте приложения и ежедневно в 08:00.
     *
     * Группы оповещений:
     * - истёк срок годности
     * - срок истекает через 1 день
     * - срок истекает в ближайшие 2 недели
     * - срок истекает в ближайший месяц
     */
    @Transactional
    @Scheduled(cron = "0 0 8 * * ?")
    public void checkProductExpiration() {
        log.info("Запуск проверки сроков годности товаров");

        List<Product> products = productRepository.findAllWithAttributeValues();
        LocalDate today = LocalDate.now();
        int notifiedCount = 0;
        int actsCreated = 0;

        for (Product product : products) {
            Optional<LocalDate> expirationOpt = productExpirationService.getExpirationDate(product);
            if (expirationOpt.isEmpty()) {
                continue;
            }

            LocalDate expirationDate = expirationOpt.get();
            long daysLeft = ChronoUnit.DAYS.between(today, expirationDate);
            String text = buildExpirationNotification(product, expirationDate, daysLeft);

            if (text == null) {
                continue;
            }

            notificationService.notifyByRole(MANAGER_ROLE, text);
            notificationService.notifyByRole(ROLE_EMPLOYEE_WAREHOUSE_MANAGER, text);
            notifiedCount++;

            if (daysLeft < 0 && product.getStockQuantity() > 0) {
                if (createAutomaticWriteOffAct(product, expirationDate)) {
                    actsCreated++;
                }
            }
        }

        log.info("Проверка сроков годности завершена. Уведомлений: {}, автосозданных актов: {}", notifiedCount, actsCreated);
    }

    private String buildExpirationNotification(Product product, LocalDate expirationDate, long daysLeft) {
        String dateText = expirationDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        if (daysLeft < 0) {
            return "🚨 Товар просрочен: «" + product.getName() + "» (артикул " + product.getArticle() + ")" +
                    ". Срок годности истёк " + dateText + ".";
        }
        if (daysLeft == 1) {
            return "⚠️ До истечения срока годности 1 день: «" + product.getName() + "» (артикул " + product.getArticle() + ")" +
                    ", дата: " + dateText + ".";
        }
        if (daysLeft <= 14) {
            return "⚠️ Товар истекает в ближайшие 2 недели: «" + product.getName() + "» (артикул " + product.getArticle() + ")" +
                    ", осталось " + daysLeft + " дн., дата: " + dateText + ".";
        }
        if (daysLeft <= 30) {
            return "ℹ️ Товар истекает в ближайший месяц: «" + product.getName() + "» (артикул " + product.getArticle() + ")" +
                    ", осталось " + daysLeft + " дн., дата: " + dateText + ".";
        }
        return null;
    }

    private boolean createAutomaticWriteOffAct(Product product, LocalDate expirationDate) {
        boolean pendingExists = writeOffActRepository.existsByProductIdAndReasonAndStatus(
                product.getId(), WriteOffReason.EXPIRED, WriteOffActStatus.PENDING_DIRECTOR
        );
        if (pendingExists) {
            return false;
        }

        Optional<Employee> responsibleOpt = findResponsibleForAutoWriteOff();
        if (responsibleOpt.isEmpty()) {
            log.warn("Не найден сотрудник для автосоздания акта списания просрочки по товару id={}", product.getId());
            return false;
        }

        Employee responsible = responsibleOpt.get();
        Optional<Warehouse> warehouseOpt = zoneProductRepository.findByProduct(product).stream()
                .filter(zp -> zp.getZone() != null && zp.getZone().getShelf() != null && zp.getZone().getShelf().getWarehouse() != null)
                .min(Comparator.comparing(ZoneProduct::getQuantity).reversed())
                .map(zp -> zp.getZone().getShelf().getWarehouse());

        String actNumber = "AUTO-WR-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + product.getId();

        WriteOffAct act = new WriteOffAct(
                actNumber,
                product,
                product.getStockQuantity(),
                WriteOffReason.EXPIRED,
                responsible
        );

        act.setStatus(WriteOffActStatus.PENDING_DIRECTOR);
        act.setComment("Автоматическое списание просроченного товара. Срок годности истёк "
                + expirationDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + ".");
        warehouseOpt.ifPresent(act::setWarehouse);

        writeOffActRepository.save(act);

        String text = "📄 Автоматически создан акт списания " + actNumber
                + " для товара «" + product.getName() + "» (просрочка). Требуется подпись руководителя.";
        notificationService.notifyByRole(MANAGER_ROLE, text);
        notificationService.notifyByRole(ROLE_EMPLOYEE_WAREHOUSE_MANAGER, text);
        return true;
    }

    private Optional<Employee> findResponsibleForAutoWriteOff() {
        List<Employee> warehouseManagers = employeeRepository.findAllByRoleName(ROLE_EMPLOYEE_WAREHOUSE_MANAGER);
        if (!warehouseManagers.isEmpty()) {
            return Optional.of(warehouseManagers.get(0));
        }

        List<Employee> managers = employeeRepository.findAllByRoleName(MANAGER_ROLE);
        if (!managers.isEmpty()) {
            return Optional.of(managers.get(0));
        }

        return Optional.empty();
    }
}
