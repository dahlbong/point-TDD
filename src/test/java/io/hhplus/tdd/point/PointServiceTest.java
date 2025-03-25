package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static io.hhplus.tdd.common.PointConstants.*;

@ExtendWith(MockitoExtension.class)
public class PointServiceTest {

    // 기본 값
    private final Long USER_ID = 1L;
    private final Long UPDATE_MILLIS = 10000L;
    private final Long CURRENT_POINT = 1000L;

    @Mock
    private UserPointTable userPointTable;
    @Mock
    private PointHistoryTable pointHistoryTable;
    @InjectMocks
    private PointService pointService;

    @Nested
    class 조회 {

        @Test
        void 유저_포인트_정상_조회() {
            //given
            given(userPointTable.selectById(USER_ID))
                    .willReturn(new UserPoint(USER_ID, CURRENT_POINT, UPDATE_MILLIS));
            //when, then
            assertThat(pointService.getPointOf(USER_ID))
                    .extracting("id", "point", "updateMillis")
                    .containsExactly(USER_ID, CURRENT_POINT, UPDATE_MILLIS);
            verify(userPointTable).selectById(USER_ID);
        }

        @Test
        void 미등록_유저일_경우_초기_포인트() {
            //given
            given(userPointTable.selectById(USER_ID))
                    .willReturn(new UserPoint(USER_ID, NEW_MEMBER_INITIAL_POINT, UPDATE_MILLIS));
            //when, then
            assertThat(pointService.getPointOf(USER_ID))
                    .extracting("id", "point", "updateMillis")
                    .containsExactly(USER_ID, NEW_MEMBER_INITIAL_POINT, UPDATE_MILLIS);
            verify(userPointTable).selectById(USER_ID);
        }

        @Test
        void 유저_포인트_히스토리_정상조회() {
            //given
            List<PointHistory> history = List.of(
                    new PointHistory(1L, USER_ID, CURRENT_POINT, TransactionType.CHARGE, UPDATE_MILLIS),
                    new PointHistory(2L, USER_ID, 400L, TransactionType.USE, 20000L),
                    new PointHistory(3L, USER_ID, 200L, TransactionType.CHARGE, 22000L)
            );
            given(pointHistoryTable.selectAllByUserId(USER_ID)).willReturn(history);

            //when
            List<PointHistory> result = pointService.getPointHistoriesOf(USER_ID);

            // then
            assertThat(result).hasSize(3)
                    .extracting("id", "userId", "amount", "type", "updateMillis")
                    .containsExactly(
                            tuple(1L, USER_ID, CURRENT_POINT, TransactionType.CHARGE, UPDATE_MILLIS),
                            tuple(2L, USER_ID, 400L, TransactionType.USE, 20000L),
                            tuple(3L, USER_ID, 200L, TransactionType.CHARGE, 22000L)
                    );
            verify(pointHistoryTable).selectAllByUserId(USER_ID);
        }
    }

    @Nested
    class 포인트_충전 {

        @Test
        void 유저_포인트_정상_충전() {
            //given
            long chargeAmount = 1L;
            long expectedBalance = CURRENT_POINT + chargeAmount;

            given(userPointTable.selectById(USER_ID))
                    .willReturn(new UserPoint(USER_ID, CURRENT_POINT, UPDATE_MILLIS));
            given(userPointTable.insertOrUpdate(USER_ID, expectedBalance))
                    .willReturn(new UserPoint(USER_ID, expectedBalance, UPDATE_MILLIS));

            //when, then
            assertThat(pointService.chargePointOf(USER_ID, chargeAmount))
                    .extracting("id", "point")
                    .containsExactly(USER_ID, expectedBalance);
            verify(userPointTable).selectById(USER_ID);
            verify(userPointTable).insertOrUpdate(USER_ID, expectedBalance);
        }

        @Test
        void 회당_최소_충전금액_미달_예외_처리 () {
            //given
            long chargeAmount = MINIMUM_CHARGE_AMOUNT - 1L;

            //when, then
            assertThatThrownBy(() -> pointService.chargePointOf(USER_ID, chargeAmount))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage(MINIMUM_CHARGE_AMOUNT + " 포인트 이상 충전해주세요");
            verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
        }

        @Test
        void 회당_최대_충전금액_초과_예외_처리 () {
            //given
            long chargeAmount = MAXIMUM_CHARGE_AMOUNT + 1L;

            //when, then
            assertThatThrownBy(() -> pointService.chargePointOf(USER_ID, chargeAmount))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("1회 최대 충전금액은  " + MAXIMUM_CHARGE_AMOUNT + "입니다.");
            verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());

        }

        @Test
        void 최대_잔고_초과_예외_처리 () {
            //given
            long chargeAmount = MAXIMUM_CHARGE_AMOUNT - CURRENT_POINT + 1L;
            given(userPointTable.selectById(USER_ID))
                    .willReturn(new UserPoint(USER_ID, CURRENT_POINT, UPDATE_MILLIS));

            //when, then
            assertThatThrownBy(() -> pointService.chargePointOf(USER_ID, chargeAmount))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("보유 가능한 최대 포인트를 초과했습니다. (최대 보유 가능 포인트: " + MAXIMUM_BALANCE + ")");
            verify(userPointTable).selectById(USER_ID);
            verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
        }
    }

    @Nested
    class 포인트_사용 {

        @Test
        void 유저_포인트_정상_사용 () {
            //given
            long useAmount = 500L;
            given(userPointTable.selectById(USER_ID))
                    .willReturn(new UserPoint(USER_ID, CURRENT_POINT, UPDATE_MILLIS));
            given(userPointTable.insertOrUpdate(USER_ID, CURRENT_POINT - useAmount))
                    .willReturn(new UserPoint(USER_ID, CURRENT_POINT - useAmount, UPDATE_MILLIS));

            //when, then
            assertThat(pointService.usePointOf(USER_ID, useAmount))
                    .extracting("id", "point", "updateMillis")
                    .containsExactly(USER_ID, CURRENT_POINT - useAmount, UPDATE_MILLIS);
            verify(userPointTable).selectById(USER_ID);
            verify(userPointTable).insertOrUpdate(USER_ID, CURRENT_POINT - useAmount);
        }

        @Test
        void 회당_최소_사용금액_미달_예외_처리 () {
            //given
            long useAmount = MINIMUM_USE_AMOUNT - 1L;

            //when, then
            assertThatThrownBy(() -> pointService.usePointOf(USER_ID, useAmount))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage(MINIMUM_USE_AMOUNT + " 포인트 이상 사용해주세요.");
        }

        @Test
        void 회당_최대_사용금액_초과_예외_처리 () {
            //given
            long useAmount = MAXIMUM_USE_AMOUNT + 1L;

            //when, then
            assertThatThrownBy(() -> pointService.usePointOf(USER_ID, useAmount))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("1회 최대 사용금액은 " + MAXIMUM_USE_AMOUNT + "입니다.");
        }

        @Test
        void 잔고를_초과한_금액_사용_시도_예외_처리 () {
            //given
            long useAmount = CURRENT_POINT + 1L;
            given(userPointTable.selectById(USER_ID)).willReturn(new UserPoint(USER_ID, CURRENT_POINT, UPDATE_MILLIS));

            //when, then
            assertThatThrownBy(() -> pointService.usePointOf(USER_ID, useAmount))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("사용할 포인트가 부족합니다. (현재 보유 포인트: " + CURRENT_POINT + ")");
        }
    }

    @Nested
    class 히스토리_저장 {

        @Test
        void 포인트_충전_시_히스토리_갱신 () {
            //given
            long chargeAmount = 500L;
            given(userPointTable.selectById(USER_ID))
                    .willReturn(new UserPoint(USER_ID, CURRENT_POINT, UPDATE_MILLIS));
            given(userPointTable.insertOrUpdate(USER_ID, CURRENT_POINT + chargeAmount))
                    .willReturn(new UserPoint(USER_ID, CURRENT_POINT + chargeAmount, UPDATE_MILLIS));
            //when
            pointService.chargePointOf(USER_ID, chargeAmount);

            //then
            verify(pointHistoryTable).insert(USER_ID, chargeAmount, TransactionType.CHARGE, UPDATE_MILLIS);
        }

        @Test
        void 포인트_사용_시_히스토리_갱신() {
            //given
            long useAmount = 500L;
            given(userPointTable.selectById(USER_ID))
                    .willReturn(new UserPoint(USER_ID, CURRENT_POINT, UPDATE_MILLIS));
            given(userPointTable.insertOrUpdate(USER_ID, CURRENT_POINT - useAmount))
                    .willReturn(new UserPoint(USER_ID, CURRENT_POINT - useAmount, UPDATE_MILLIS));

            //when
            pointService.usePointOf(USER_ID, useAmount);

            //then
            verify(pointHistoryTable).insert(USER_ID, useAmount, TransactionType.USE, UPDATE_MILLIS);
        }

        @Test
        void 충전_실패시_히스토리_저장되지_않음() {
            //given
            long chargeAmount = MAXIMUM_CHARGE_AMOUNT + 1L;

            //then
            assertThatThrownBy(() -> pointService.chargePointOf(USER_ID, chargeAmount))
                    .isInstanceOf(RuntimeException.class);

            verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
        }

        @Test
        void 사용_실패시_히스토리_저장되지_않음() {
            //given
            long useAmount = CURRENT_POINT + 1L;

            //then
            assertThatThrownBy(() -> pointService.usePointOf(USER_ID, useAmount))
                    .isInstanceOf(RuntimeException.class);
            verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
        }
    }
}
