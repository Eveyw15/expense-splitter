package com.elec5619.group3.trip_planner_backend.dto;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class ExpenseRequest {
    public enum SplitMode { EQUAL, CUSTOM, BY_ITEM }
    @NotNull private SplitMode splitMode;
    @NotEmpty @Valid
    private java.util.List<@NotNull Participant> participants;
    @Valid private java.util.List<@NotNull Item> items;

    @Getter @Setter
    public static class Item {
        @NotBlank @Size(max = 200) private String name;
        @Min(1) private Integer quantity;
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) private BigDecimal amount;
        @NotEmpty private java.util.List<@NotNull Long> userIds;
    }

    @Getter
    @Setter
    public static class Participant {
        @NotNull private Long userId;
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2)
        private BigDecimal shareAmount;
    }

    @NotNull private Long payerId;
    @NotBlank @Size(max = 200) private String description;
    @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2)
    private BigDecimal amount;
    private String currencyCode;
    @NotNull private LocalDate date;
    @Size(max = 50) private String category;
}
