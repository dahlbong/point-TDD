package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;

@RequiredArgsConstructor
@Service
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    // 유저 별 락 저장소
    private final ConcurrentHashMap<Long, StampedLock> userLocks = new ConcurrentHashMap<>();

    // 유저별 동기화 객체 획득
    private StampedLock getLockForUser(long userId) {
        return userLocks.computeIfAbsent(userId, id -> new StampedLock());
    }

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
        StampedLock lock = getLockForUser(userId);
        long stamp = lock.writeLock();

        try {
            // 충전 금액 검증
            PointValidator.validateChargeAmount(amount);

            // 잔액 검증
            UserPoint current = getUserOf(userId);
            PointValidator.validateChargeBalance(current.point(), amount);
            UserPoint charged = current.charge(amount);

            return updatePointBalance(charged, TransactionType.CHARGE, amount);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    // 포인트 사용
    public UserPoint usePointOf(long userId, long amount) {
        StampedLock lock = getLockForUser(userId);
        long stamp = lock.writeLock();

        try {
            // 사용 금액 검증
            PointValidator.validateUseAmount(amount);

            // 잔액 검증
            UserPoint current = getUserOf(userId);
            PointValidator.validateSufficientBalance(current.point(), amount);
            UserPoint used = current.use(amount);

            return updatePointBalance(used, TransactionType.USE, amount);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    // 포인트 조회 - 읽기 lock
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
