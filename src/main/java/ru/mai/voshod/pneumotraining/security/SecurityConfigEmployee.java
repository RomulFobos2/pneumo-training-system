package ru.mai.voshod.pneumotraining.security;


import com.mai.siarsp.service.employee.EmployeeService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration
@EnableWebSecurity
@Order(1)
public class SecurityConfigEmployee {

    private final EmployeeService employeeService;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final AuthenticationLoggingSuccessHandler authenticationLoggingSuccessHandler;
    private final AuthenticationLoggingFailureHandler authenticationLoggingFailureHandler;

    public SecurityConfigEmployee(EmployeeService employeeService,
                                  BCryptPasswordEncoder bCryptPasswordEncoder,
                                  AuthenticationLoggingSuccessHandler authenticationLoggingSuccessHandler,
                                  AuthenticationLoggingFailureHandler authenticationLoggingFailureHandler) {
        this.employeeService = employeeService;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.authenticationLoggingSuccessHandler = authenticationLoggingSuccessHandler;
        this.authenticationLoggingFailureHandler = authenticationLoggingFailureHandler;
    }

    @Bean
    public SecurityFilterChain employeeSecurityFilterChain(HttpSecurity http, AccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .securityMatcher("/employee/**", "/api/mobile/**", "/static/**", "/images/**", "/css/**", "/js/**", "/", "/image/**", "/logout")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/static/**", "/images/**", "/css/**", "/js/**", "/", "/image/**").permitAll()
                        .requestMatchers("/api/mobile/**").hasAnyRole("EMPLOYEE_ADMIN", "EMPLOYEE_MANAGER", "EMPLOYEE_WAREHOUSE_MANAGER", "EMPLOYEE_WAREHOUSE_WORKER", "EMPLOYEE_COURIER", "EMPLOYEE_ACCOUNTER")
                        .requestMatchers("/employee/warehouseManager/warehouse-management/find-product")
                        .hasAnyRole("EMPLOYEE_ADMIN", "EMPLOYEE_MANAGER", "EMPLOYEE_WAREHOUSE_MANAGER", "EMPLOYEE_WAREHOUSE_WORKER", "EMPLOYEE_COURIER", "EMPLOYEE_ACCOUNTER")
                        .requestMatchers("/employee/admin/**").hasRole("EMPLOYEE_ADMIN")
                        .requestMatchers("/employee/manager/**").hasRole("EMPLOYEE_MANAGER")
                        .requestMatchers("/employee/warehouseManager/**").hasRole("EMPLOYEE_WAREHOUSE_MANAGER")
                        .requestMatchers("/employee/accounter/**").hasRole("EMPLOYEE_ACCOUNTER")
                        .requestMatchers("/employee/warehouseWorker/**").hasRole("EMPLOYEE_WAREHOUSE_WORKER")
                        .requestMatchers("/employee/courier/**").hasRole("EMPLOYEE_COURIER")
                        .requestMatchers("/employee/**").hasAnyRole("EMPLOYEE_ADMIN", "EMPLOYEE_MANAGER", "EMPLOYEE_WAREHOUSE_MANAGER", "EMPLOYEE_WAREHOUSE_WORKER", "EMPLOYEE_COURIER", "EMPLOYEE_ACCOUNTER") // любые сотрудники
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/employee/login")
                        .loginProcessingUrl("/employee/login")
                        .successHandler(authenticationLoggingSuccessHandler)
                        .failureHandler(authenticationLoggingFailureHandler)
                        .permitAll()
                )
                .logout(logout -> logout.permitAll().logoutSuccessUrl("/"))
                .exceptionHandling(eh -> eh.accessDeniedHandler(accessDeniedHandler))
                .authenticationProvider(employeeAuthenticationProvider());
        return http.build();
    }

    @Bean
    public AuthenticationProvider employeeAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(employeeService);
        provider.setPasswordEncoder(bCryptPasswordEncoder);
        return provider;
    }


}
