import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * In this chapter we are going to cover fundamentals of how to create a sequence. At the end of this
 * chapter we will tackle more complex methods like generate, create, push, and we will meet them again in following
 * chapters like Sinks and Backpressure.
 *
 * Read first:
 *
 * https://projectreactor.io/docs/core/release/reference/#which.create
 * https://projectreactor.io/docs/core/release/reference/#producing
 * https://projectreactor.io/docs/core/release/reference/#_simple_ways_to_create_a_flux_or_mono_and_subscribe_to_it
 *
 * Useful documentation:
 *
 * https://projectreactor.io/docs/core/release/reference/#which-operator
 * https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html
 * https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html
 *
 * @author Stefan Dragisic
 */
public class c5_CreatingSequence {

    /**
     * Emit value that you already have.
     */
    @Test
    public void value_I_already_have_mono() {
        String valueIAlreadyHave = "value";
        Mono<String> valueIAlreadyHaveMono = Mono.just(valueIAlreadyHave);

        StepVerifier.create(valueIAlreadyHaveMono)
                    .expectNext("value")
                    .verifyComplete();
    }

    /**
     * Emit potentially null value that you already have.
     */
    @Test
    public void potentially_null_mono() {
        String potentiallyNull = null;
        Mono<String> potentiallyNullMono = Mono.justOrEmpty(potentiallyNull);
        // justOrEmpty 는 item 이 null 이면 onComplete 를 발생시키고 아니면 item 을 내보내고 onComplete 를 발생시킨다.
        // just 로 하면 NPE 발생한다..
        // https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#justOrEmpty-T-

        StepVerifier.create(potentiallyNullMono)
                    .verifyComplete();
    }

    /**
     * Emit value from a optional.
     */
    @Test
    public void optional_value() {
        Optional<String> optionalValue = Optional.of("optional");
        Mono<String> optionalMono = Mono.justOrEmpty(optionalValue);
        // justOrEmpty 는 Optional 도 파라미터로 받게끔 오버로딩 되어있다.
        // Create a new Mono that emits the specified item if Optional.isPresent() otherwise only emits onComplete.
        // https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#justOrEmpty-java.util.Optional-

        StepVerifier.create(optionalMono)
                    .expectNext("optional")
                    .verifyComplete();
    }

    /**
     * Convert callable task to Mono.
     */
    @Test
    public void callable_counter() {
        AtomicInteger callableCounter = new AtomicInteger(0);
        Callable<Integer> callable = () -> {
            System.out.println("You are incrementing a counter via Callable!");
            return callableCounter.incrementAndGet();
        };

        Mono<Integer> callableCounterMono = Mono.fromCallable(callable);

        StepVerifier.create(callableCounterMono.repeat(2))
                    .expectNext(1, 2, 3)
                    .verifyComplete();
    }

    /**
     * Convert Future task to Mono.
     */
    @Test
    public void future_counter() throws InterruptedException {
        AtomicInteger futureCounter = new AtomicInteger(0);
        CompletableFuture<Integer> completableFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("[" + Thread.currentThread().getName() + "]" + "You are incrementing a counter via Future!");
            return futureCounter.incrementAndGet();
        });

        Thread.sleep(1000);
        System.out.println("is completableFuture: " + completableFuture.isDone());

        Mono<Integer> futureCounterMono = Mono.fromFuture(completableFuture)
                .doOnNext(i -> System.out.println("[" + Thread.currentThread().getName() + "]" + "doOnNext: " + i));

        /**
         * completableFuture 로그는 ForkJoinPool.commonPool 로 찍힌다.
         * 그리고..
         * todo, doOnNext 의 로그가 main 스레드로 찍힌다..
         * 그럼 fromFuture 는 map 처럼 동기식 연산자인가.. ?
         * 나는.. doOnNext 로그가 ForkJoinPool.commonPool 로 찍힐 것으로 예상했다..
         *
         * 좀 생각해보니..
         * 동기식 연산자로 생각된다..
         * completableFuture 는 supplyAsync 연산자 호출 순간 이미 실행을 한다.
         * StepVerifier 의 subscribe 에 의해 실행 되지 않음
         * 따라서 fromFuture 실행할 땐 이미 결과가 존재하고 그것을 item 으로 흘려보내기만 하면 되는듯..
         */

        StepVerifier.create(futureCounterMono)
                    .expectNext(1)
                    .verifyComplete();
    }

    /**
     * Convert Runnable task to Mono.
     */
    @Test
    public void runnable_counter() {
        AtomicInteger runnableCounter = new AtomicInteger(0);
        Runnable runnable = () -> {
            runnableCounter.incrementAndGet();
            System.out.println("You are incrementing a counter via Runnable!");
        };
        Mono<Integer> runnableMono = Mono.fromRunnable(runnable);

        StepVerifier.create(runnableMono.repeat(2))
                    .verifyComplete();

        Assertions.assertEquals(3, runnableCounter.get());
    }

    /**
     * Create Mono that emits no value but completes successfully.
     */
    @Test
    public void acknowledged() {
        Mono<String> acknowledged = Mono.empty();

        StepVerifier.create(acknowledged)
                    .verifyComplete();
    }

    /**
     * Create Mono that emits no value and never completes.
     */
    @Test
    public void seen() {
        Mono<String> seen = Mono.never();
        // https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#never--

        StepVerifier.create(seen.timeout(Duration.ofSeconds(5)))
                    .expectSubscription()
                    .expectNoEvent(Duration.ofSeconds(4))
                    .verifyTimeout(Duration.ofSeconds(5));
    }

    /**
     * Create Mono that completes exceptionally with exception `IllegalStateException`.
     */
    @Test
    public void trouble_maker() {
        Mono<String> trouble = Mono.error(new IllegalStateException());

        StepVerifier.create(trouble)
                    .expectError(IllegalStateException.class)
                    .verify();
    }

    /**
     * Create Flux that will emit all values from the array.
     */
    @Test
    public void from_array() {
        Integer[] array = {1, 2, 3, 4, 5};
        Flux<Integer> arrayFlux = Flux.fromArray(array);

        StepVerifier.create(arrayFlux)
                    .expectNext(1, 2, 3, 4, 5)
                    .verifyComplete();
    }

    /**
     * Create Flux that will emit all values from the list.
     */
    @Test
    public void from_list() {
        List<String> list = Arrays.asList("1", "2", "3", "4", "5");
        Flux<String> listFlux = Flux.fromIterable(list);

        StepVerifier.create(listFlux)
                    .expectNext("1", "2", "3", "4", "5")
                    .verifyComplete();
    }

    /**
     * Create Flux that will emit all values from the stream.
     */
    @Test
    public void from_stream() {
        Stream<String> stream = Stream.of("5", "6", "7", "8", "9");
        Flux<String> streamFlux = Flux.fromStream(stream);

        StepVerifier.create(streamFlux)
                    .expectNext("5", "6", "7", "8", "9")
                    .verifyComplete();
    }

    /**
     * Create Flux that emits number incrementing numbers at interval of 1 second.
     */
    @Test
    public void interval() {
        Flux<Long> interval = Flux.interval(Duration.ofSeconds(1));
        // 아래도 동일..
//        Flux<Long> interval = Flux.fromIterable(List.of(0L, 1L, 2L))
//                        .delayElements(Duration.ofSeconds(1));

        System.out.println("Interval: ");
        StepVerifier.create(interval.take(3).doOnNext(System.out::println))
                    .expectSubscription()
                    .expectNext(0L)
                    .expectNoEvent(Duration.ofMillis(900))
                    .expectNext(1L)
                    .expectNoEvent(Duration.ofMillis(900))
                    .expectNext(2L)
                    .verifyComplete();
    }

    /**
     * Create Flux that emits range of integers from [-5,5].
     */
    @Test
    public void range() {
        Flux<Integer> range = Flux.range(-5, 11);

        System.out.println("Range: ");
        StepVerifier.create(range.doOnNext(System.out::println))
                    .expectNext(-5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5)
                    .verifyComplete();
    }

    /**
     * Create Callable that increments the counter and returns the counter value, and then
     * use `repeat()` operator to create Flux that emits values from 0 to 10.
     */
    @Test
    public void repeat() {
        AtomicInteger counter = new AtomicInteger(0);
        Flux<Integer> repeated = Mono.fromCallable(counter::incrementAndGet)
                        .repeat(9); // mono publisher 를 여러번 subscribe 하여 flux publisher 로 만든다..
        // https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#repeat--

        System.out.println("Repeat: ");
        StepVerifier.create(repeated.doOnNext(System.out::println))
                    .expectNext(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
                    .verifyComplete();
    }

    /**
     * Following example is just a basic usage of `generate,`create`,`push` sinks.
     * We will learn how to use them in a more complex scenarios when we tackle backpressure.
     *
     * Answer:
     * - What is difference between `generate` and `create`?
     * - What is difference between `create` and `push`?
     */
    @Test
    public void generate_programmatically() {

        /**
         * generate : 동기적으로 동작하며, 상태 기반의 값을 순차적으로 생성
         * create : 멀티 스레드 환경에서 비동기적으로 값을 생성하고 방출 (이벤트 드리븐 방식의 데이터를 처리할 때 유용)
         * push : 단일 스레드 환경에서 비동기적으로 값을 생성하고 방출 (단일 스레드 기반의 프로듀싱 작업에 유용)
         */

        AtomicInteger counter = new AtomicInteger(0);
        Flux<Integer> generateFlux = Flux.generate(sink -> {
            // 이전 상태에 관심이 있으므로 동기적이라 할 수 있다.. 해당 코드 블럭은 순차적으로 한번씩 여러번 수행된다.
            if(counter.get() > 5) {
                System.out.println("[generate] onComplete, tx=" + Thread.currentThread().getName());
                sink.complete();
            }
            System.out.println("[generate] onNext=" + counter.get() + ", tx=" + Thread.currentThread().getName());
            sink.next(counter.getAndIncrement());
        });

        //------------------------------------------------------

        Flux<Integer> createFlux = Flux.create(sink -> {
            // 해당 코드블럭에서 멀티 스레드로 sink.next 를 호출해볼 수 있다. create 연산자의 마블 다이어그램을 보자!
            IntStream.range(0, 6)
                    .forEach(
                            i -> {
                                System.out.println("[create] onNext=" + i + ", tx=" + Thread.currentThread().getName());
                                sink.next(i);
                            }
                    );
            System.out.println("[create] onComplete, tx=" + Thread.currentThread().getName());
            sink.complete();
        });

        //------------------------------------------------------

        Flux<Integer> pushFlux = Flux.push(sink -> {
            // 해당 코드 블럭은 1개의 스레드로 동작해야만한다?
            IntStream.range(0, 6)
                    .forEach(
                            i -> {
                                System.out.println("[push] onNext=" + i + ", tx=" + Thread.currentThread().getName());
                                sink.next(i);
                            }
                    );
            System.out.println("[push] onComplete, tx=" + Thread.currentThread().getName());
            sink.complete();
        });

        StepVerifier.create(generateFlux.doOnNext(i -> System.out.println("[generate] doOnNext=" + i + ", tx=" + Thread.currentThread().getName())))
                    .expectNext(0, 1, 2, 3, 4, 5)
                    .verifyComplete();

        StepVerifier.create(createFlux.doOnNext(i -> System.out.println("[create] doOnNext=" + i + ", tx=" + Thread.currentThread().getName())))
                    .expectNext(0, 1, 2, 3, 4, 5)
                    .verifyComplete();

        StepVerifier.create(pushFlux.doOnNext(i -> System.out.println("[push] doOnNext=" + i + ", tx=" + Thread.currentThread().getName())))
                    .expectNext(0, 1, 2, 3, 4, 5)
                    .verifyComplete();
    }

    /**
     * Something is wrong with the following code. Find the bug and fix it so test passes.
     */
    @Test
    public void multi_threaded_producer() {

        Flux<Integer> producer = Flux.create(sink -> { // push 를 사용하면 sink.next 가 thread-safe 하지 않게 동작하여 몇몇이 씹히기 때문에 create 로 해야한다.
            for (int i = 0; i < 100; i++) {
                int finalI = i;
                new Thread(() -> sink.next(finalI)).start(); //don't change this line!
            }
        });

        //do not change code below
        StepVerifier.create(producer
                                    .doOnNext(System.out::println)
                                    .take(100)) // take 연산자 덕분에 create 연산자 내부에서 onComplete 이벤트를 발행하지 않아도 됨
                    .expectNextCount(100)
                    .verifyComplete();
    }
}
