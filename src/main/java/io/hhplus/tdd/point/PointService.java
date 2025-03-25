package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    // 포인트 조회
    public UserPoint getPointOf(long userId) {
        return userPointTable.selectById(userId);
    }

    // 히스토리 조회
    public List<PointHistory> getPointHistoriesOf(long userId) {
        return pointHistoryTable.selectAllByUserId(userId);
    }

    // 포인트 충전
    public UserPoint chargePointOf(long userId, long amount) {
        // 충전 금액 검증
        PointValidator.validateChargeAmount(amount);
        long currentPoint = currentPointOf(userId);
        // 잔액 검증
        PointValidator.validateChargeBalance(currentPoint, amount);
        long balance = currentPoint + amount;

        return updatePointBalance(userId, balance, TransactionType.CHARGE);
    }

    // 포인트 사용
    public UserPoint usePointOf(long userId, long amount) {
        // 사용 금액 검증
        PointValidator.validateUseAmount(amount);
        long currentPoint = currentPointOf(userId);
        // 잔액 검증
        PointValidator.validateSufficientBalance(currentPoint, amount);
        long balance = currentPoint - amount;
        return updatePointBalance(userId, balance, TransactionType.USE);
    }

    // 포인트 사용 or 충전 시 잔액 갱신
    public UserPoint updatePointBalance(long userId, long newAmount, TransactionType type) {
        UserPoint updatedPoint = userPointTable.insertOrUpdate(userId, newAmount);
        pointHistoryTable.insert(userId, newAmount, type, currentTime());
        return updatedPoint;
    }


    private long currentPointOf(long userId) {
        return getPointOf(userId).point();
    }

    private long currentTime() {
        return System.currentTimeMillis();
    }

}
