# 동기화 방식 선택 전략

멀티스레드 환경에서의 안정성과 성능을 확보하기 위해 적절한 동기화 전략을 선택하는 것이 중요합니다. 
포인트 충전과 사용에 대한 원자성을 보장하기 위해 StampedLock과 ConcurrentHashMap을 선택했습니다.
공식문서를 바탕으로 위 방식을 선택한 이유를 Java 공식문서를 바탕으로 설명하겠습니다.

---

## 📌 요약

| 방식             | 가시성 | 유연성       | 사용 난이도 | 사용 예                       |   선택 |
|-----------------|--------|--------------|--------------|--------------------------------|---|
| synchronized      | ✅     | ❌           | ⭐⭐           | 임계영역 단순 보호              |X|
| ReentrantLock    | ✅     | ✅           | ⭐⭐⭐          | 조건 대기, 타임아웃 필요 시     |X|
| Atomic 변수       | ✅     | ❌           | ⭐            | 카운터, 플래그 등 간단한 연산    |X|
| StampedLock        | ✅     | ✅ (낙관적)  | ⭐⭐⭐⭐         | 읽기/쓰기 비율이 높은 환경      |O|
| concurrent 패키지 | ✅     | ✅           | ⭐⭐⭐          | 병렬 처리, 큐, 맵 등 고수준 제어 |O|

---
## 1️⃣동시성 제어 방식 및 장단점
### 1. `synchronized`
> 메서드나 블록 단위로 임계 구역을 지정하여 스레드 간의 동시 접근을 제어합니다. `synchronized` 키워드를 사용하면 해당 코드 블록이 한 번에 하나의 스레드만 실행되도록 보장합니다.

**장점**
- 사용법이 간단하고 직관적입니다.
- JVM 수준에서 지원되므로 안정성이 높습니다.
- 별도의 객체 생성 없이 사용 가능합니다.

**단점**
- 유연성이 부족합니다 (예: 조건 대기, 타임아웃 등 불가).
- 긴 작업이 포함된 경우 다른 스레드가 장시간 대기해야 할 수 있습니다.
- 블로킹 기반이므로 성능 저하 가능성이 있습니다.

### 2. `Atomic` 변수 클래스 
>`AtomicInteger`, `AtomicLong`, `AtomicReference` 등 원자적 연산을 제공하는 클래스입니다. 내부적으로 CAS(Compare-And-Swap) 연산을 사용하여 동기화를 수행합니다.

**장점**
- Non-blocking 방식으로 동기화 성능이 뛰어납니다.
- 간단한 연산(CAS 기반)에는 가장 효과적입니다.

**단점**
- 복잡한 상태 동기화에는 부적합합니다.
- 여러 변수의 동기화에는 사용하기 어렵습니다.

### 3. `ReentrantLock` 클래스
> 명시적으로 락을 제어할 수 있는 클래스입니다. `tryLock()`, `lockInterruptibly()` 등을 통해 유연한 제어가 가능합니다.

**장점**
- 조건 변수(`Condition`)를 이용해 `wait/notify`보다 더 정교한 스레드 제어가 가능합니다.
- fairness 옵션을 제공하여 락의 공정한 획득 순서를 보장할 수 있습니다.
- 타임아웃이나 인터럽트 처리가 가능합니다.

**단점**
- `lock()`과 `unlock()`을 반드시 쌍으로 사용해야 하며, 실수 시 데드락이 발생할 수 있습니다.
- 코드가 다소 복잡해질 수 있습니다.

### 4. `StampedLock` (Java 8+)
>`ReadWriteLock`의 확장판으로, 읽기/쓰기 잠금 외에도 **낙관적 락(Optimistic Read Lock)**을 제공하여 성능 향상에 기여합니다. `StampedLock`은 (쓰기/읽기/낙관적 읽기) 락을 지원합니다:

**장점**
- 낙관적 읽기로 성능 향상이 가능합니다. 실제 쓰기 충돌이 드문 경우에 유리합니다.
- 명확한 읽기/쓰기 분리로 높은 동시성을 확보할 수 있습니다.
- `tryConvertToWriteLock()` 등을 이용한 동적 전환이 가능합니다.

**단점**
- `unlock` 시 스탬프 값을 반드시 전달해야 하므로 사용이 복잡합니다.
- 재진입이 불가능합니다. 같은 스레드가 중복하여 락을 획득할 수 없습니다.
- 사용 오류 시 디버깅이 어려울 수 있습니다.


### 5. `java.util.concurrent` 패키지
> - `ConcurrentHashMap`: thread-safe 해시맵
> - `CopyOnWriteArrayList`: 읽기 위주 환경에 적합한 리스트
> - `CountDownLatch`, `CyclicBarrier`, `Semaphore`: 병렬 처리 제어를 위한 유틸리티 클래스

**장점**
- 고성능, 고수준의 동시성 제어 기능을 제공합니다.
- 복잡한 스레드 협업 작업에 적합합니다.

**단점**
- 특정 목적에 맞는 클래스를 선택해야 하므로 학습 곡선이 있습니다.

---

## 2️⃣️원자성 보장 방식 선택 이유

본 서비스는 포인트 충전과 사용 이력이 계속해서 쌓이는 특성상, 읽기와 쓰기의 성능 균형이 중요합니다. 또한 사용자 단위로 데이터 정합성을 확보하기 위해 세밀한 제어가 필요했습니다.
Java 공식 문서에서 추천하는 바와 같이, 높은 성능과 데이터 정합성을 동시에 만족시키기 위해 `StampedLock`과 `ConcurrentHashMap`을 선택하게 되었습니다.
---
## 3️⃣구현 방식

 사용자 단위로 별도의 `StampedLock` 객체를 생성하여 UserLock을 `ConcurrentHashMap`에 저장하여 관리합니다.
- 병렬 처리: 여러 사용자가 각자의 데이터를 동시에 접근할 수 있습니다.
- 상태 동기화: 한 사용자의 포인트에 여러 스레드가 접근할 경우, 해당 사용자에 대한 데이터 접근은 순차적으로 처리됩니다.
```JAVA
// 유저 별 락 저장소
private final ConcurrentHashMap<Long, StampedLock> userLocks = new ConcurrentHashMap<>();

// 유저별 동기화 객체 획득
private StampedLock getLockForUser(long userId) {
    return userLocks.computeIfAbsent(userId, id -> new StampedLock());
}

...

// 포인트 충전
    public UserPoint chargePointOf(long userId, long amount) {
        StampedLock lock = getLockForUser(userId);
        
        long stamp = lock.writeLock();
        // ------ ▼ 임계 구역 ▼ ------
        try {
            ...
        }
        // ------ ▲ 임계 구역 ▲ ------ 
        finally {
            lock.unlockWrite(stamp);
        }
        
    }
```

---
## 4️⃣결과
100개의 쓰레드의 포인트 충전과 사용을 동시에 발생시켰을 때, 원자성을 보장하는 것을 통합테스트를 통해 확인했습니다.

---
## 📎 참고 자료
- [Java 공식 튜토리얼 - 동시성](https://docs.oracle.com/javase/tutorial/essential/concurrency/)
- [Java SE 8 API 문서 - `StampedLock` 클래스](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/locks/StampedLock.html)

