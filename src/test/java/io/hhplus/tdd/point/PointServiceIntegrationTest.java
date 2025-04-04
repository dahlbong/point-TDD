package io.hhplus.tdd.point;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static io.hhplus.tdd.common.PointConstants.*;
import static io.hhplus.tdd.common.PointConstants.MINIMUM_CHARGE_AMOUNT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
public class PointServiceIntegrationTest {

    @Autowired
    private PointService pointService;

    private static final Logger log = LoggerFactory.getLogger(PointServiceIntegrationTest.class);

    private final Long USER_ID = 1L;
    private final Long CHARGE_AMOUNT = 1000L;
    private final Long USE_AMOUNT = 100L;

    @Nested
    class 동시성 {

        @Test
        void 한_유저의_포인트를_동시에_충전할_경우_잔액_정상_반영() throws InterruptedException {
            //given
            final long CURRENT_POINT = getPointOf(USER_ID);
            //when
            executeConcurrent(COMMON_THREAD_COUNT, index -> pointService.chargePointOf(USER_ID, CHARGE_AMOUNT));

            //then
            UserPoint result = pointService.getUserPointOf(USER_ID);
            assertThat(result.point()).isEqualTo(CURRENT_POINT + CHARGE_AMOUNT * COMMON_THREAD_COUNT);
        }

        // 번갈하가며 충전 & 사용 시도
        @Test
        void 한_유저의_포인트를_동시에_충전하고_사용할_경우_잔액_정상_반영() throws InterruptedException {
            //given
            final long CURRENT_POINT = getPointOf(USER_ID);
            //when
            executeConcurrent(COMMON_THREAD_COUNT, i -> {
                if (i % 2 == 0) pointService.chargePointOf(USER_ID, CHARGE_AMOUNT);
                else pointService.usePointOf(USER_ID, USE_AMOUNT);
            });
            //then
            long expected = CURRENT_POINT
                    + (COMMON_THREAD_COUNT / 2 * CHARGE_AMOUNT)
                    - (COMMON_THREAD_COUNT / 2 * USE_AMOUNT);
            UserPoint result = pointService.getUserPointOf(USER_ID);
            assertThat(result.point()).isEqualTo(expected);
        }

        @Test
        void 많은_동시_거래_시도() throws InterruptedException{
            //given
            final long SMALL_CHARGE_AMOUNT = 20L;
            final long SMALL_USE_AMOUNT = 10L;
            final long CURRENT_POINT = getPointOf(USER_ID);
            //when
            executeConcurrent(EXTREME_THREAD_COUNT, i -> {
                if (i % 2 == 0) pointService.chargePointOf(USER_ID, SMALL_CHARGE_AMOUNT);
                else pointService.usePointOf(USER_ID, SMALL_USE_AMOUNT);
            });
            //then
            long expected = CURRENT_POINT
                    + (EXTREME_THREAD_COUNT / 2 * SMALL_CHARGE_AMOUNT)
                    - (EXTREME_THREAD_COUNT / 2 * SMALL_USE_AMOUNT);
            UserPoint result = pointService.getUserPointOf(USER_ID);
            assertThat(result.point()).isEqualTo(expected);
        }
    }

    @Nested
    class 기본적인_통합_테스트 {

        @Test
        void 최소_충전금액_미만_충전_시도_예외_처리() {
            //given
            long chargeAmount = MINIMUM_CHARGE_AMOUNT - 1L;

            //when, then
            assertThatThrownBy(() -> pointService.chargePointOf(USER_ID, chargeAmount))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("포인트 이상 충전해주세요.");
        }

        @Test
        void 최대_잔액_초과_충전_예외_처리() {
            //given
            setUserPoint(USER_ID, 1000L);
            long chargeAmount = MAXIMUM_BALANCE - getPointOf(USER_ID) + 1L;
            //when
            assertThatThrownBy(() -> pointService.chargePointOf(USER_ID, chargeAmount))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("보유 가능한 최대 포인트를 초과했습니다");
        }

        @Test
        void 잔액_초과_사용_시도_예외_처리() {
            //given
            long useAmount = getPointOf(USER_ID) + 1L;

            //when, then
            assertThatThrownBy(() -> pointService.usePointOf(USER_ID, useAmount))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("사용할 포인트가 부족합니다.");
        }
    }


    public long getPointOf(long userId) {
        return pointService.getUserPointOf(userId).point();
    }

    public void setUserPoint(Long userId, Long initialPoint) {
        pointService.chargePointOf(userId, initialPoint);
    }

    // 경쟁상태 발생 메서드
    public void executeConcurrent(int threadCount, Consumer<Integer> task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    log.info("[스레드 {}] 시작", index);
                    task.accept(index);
                    successCount.incrementAndGet();
                    log.info("[스레드 {}] 성공", index);
                } catch (Exception e) {
                    log.error("[[스레드 {}] 실패: {}", index, e.getMessage(), e);
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                    log.info("[스레드 {}] 종료", index);
                }
            });
        }

        latch.await();
        executor.shutdown();
    }


}
