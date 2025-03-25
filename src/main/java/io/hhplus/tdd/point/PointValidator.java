package io.hhplus.tdd.point;

import static io.hhplus.tdd.common.PointConstants.*;

public class PointValidator {

    public static void validateChargeAmount(long amount) {
        validateMinimumAmount(amount, MINIMUM_CHARGE_AMOUNT, "충전");
        validateMaximumAmount(amount, MAXIMUM_CHARGE_AMOUNT, "충전");
    }

    public static void validateChargeBalance(long currentPoint, long amount) {
        if (currentPoint + amount > MAXIMUM_BALANCE) {
            throw new IllegalArgumentException(String.format("보유 가능한 최대 포인트를 초과했습니다. (최대 보유 가능 포인트: %d)", MAXIMUM_BALANCE));
        }
    }

    public static void validateUseAmount(long amount) {
        validateMinimumAmount(amount, MINIMUM_USE_AMOUNT, "사용");
        validateMaximumAmount(amount, MAXIMUM_USE_AMOUNT, "사용");
    }

    public static void validateSufficientBalance(long currentPoint, long amount) {
        if (currentPoint < amount) {
            throw new IllegalArgumentException(String.format("사용할 포인트가 부족합니다. (현재 보유 포인트: %d)", currentPoint));
        }
    }

    private static void validateMinimumAmount(long amount, long minimumAmount, String transactionType) {
        if (amount < minimumAmount) {
            throw new IllegalArgumentException(String.format(
                    "%d 포인트 이상 %s해주세요.",
                    minimumAmount,
                    transactionType
            ));
        }
    }

    private static void validateMaximumAmount(long amount, long maximumAmount, String transactionType) {
        if (amount > maximumAmount) {
            throw new IllegalArgumentException(String.format(
                    "1회 최대 %s금액은 %d입니다.",
                    transactionType,
                    maximumAmount
            ));
        }
    }

    private PointValidator() {
    }
}
