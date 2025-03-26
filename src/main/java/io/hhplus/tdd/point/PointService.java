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
    public UserPoint getUserPointOf(long userId) {
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

        // 잔액 검증
        UserPoint current = getUserOf(userId);
        PointValidator.validateChargeBalance(current.point(), amount);
        UserPoint charged = current.charge(amount);

        return updatePointBalance(charged, TransactionType.CHARGE, amount);
    }

    // 포인트 사용
    public UserPoint usePointOf(long userId, long amount) {
        // 사용 금액 검증
        PointValidator.validateUseAmount(amount);

        // 잔액 검증
        UserPoint current = getUserOf(userId);
        PointValidator.validateSufficientBalance(current.point(), amount);
        UserPoint used = current.use(amount);

        return updatePointBalance(used, TransactionType.USE, amount);
    }

    public UserPoint getUserOf(long userId) {
        UserPoint current = userPointTable.selectById(userId);
        return current;
    }

    // 포인트 사용 or 충전 시 잔액 갱신
    public UserPoint updatePointBalance(UserPoint userPoint, TransactionType type, long transactionAmount) {
        UserPoint updatedPoint = userPointTable.insertOrUpdate(userPoint.id(), userPoint.point());
        pointHistoryTable.insert(userPoint.id(), transactionAmount, type, updatedPoint.updateMillis());
        return updatedPoint;
    }

}
