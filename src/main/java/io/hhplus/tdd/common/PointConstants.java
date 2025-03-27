package io.hhplus.tdd.common;

public class PointConstants {

    //비즈니스 요구사항
    public static final long NEW_MEMBER_INITIAL_POINT = 0L;
    public static final long MINIMUM_CHARGE_AMOUNT = 1L;
    public static final long MAXIMUM_CHARGE_AMOUNT = 100000L;
    public static final long MINIMUM_USE_AMOUNT = 1L;
    public static final long MAXIMUM_USE_AMOUNT = 10000L;
    public static final long MAXIMUM_BALANCE = 100000L;

    //쓰레드풀
    public static final int COMMON_THREAD_COUNT = 10;
    public static final int EXTREME_THREAD_COUNT = 100;

    //인스턴스화 방지
    private PointConstants() {
    }
}
