import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Often we might require state when working with complex streams. Reactor offers powerful context mechanism to share
 * state between operators, as we can't rely on thread-local variables, because threads are not guaranteed to be the
 * same. In this chapter we will explore usage of Context API.
 *
 * Read first:
 *
 * https://projectreactor.io/docs/core/release/reference/#context
 *
 * Useful documentation:
 *
 * https://projectreactor.io/docs/core/release/reference/#which-operator
 * https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html
 * https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html
 *
 * @author Stefan Dragisic
 */
public class c13_Context extends ContextBase {

    /**
     * You are writing a message handler that is executed by a framework (client). Framework attaches a http correlation
     * id to the Reactor context. Your task is to extract the correlation id and attach it to the message object.
     */
    public Mono<Message> messageHandler(String payload) {

        return Mono.deferContextual(contextView -> { // defer 에서 contextView 를 파라미터로 넣어주는 연산자이다.
            String correlationId = contextView.get(HTTP_CORRELATION_ID);
            return Mono.just(new Message(correlationId, payload));
        });
    }

    @Test
    public void message_tracker() {
        //don't change this code
        Mono<Message> mono = messageHandler("Hello World!")
                .contextWrite(Context.of(HTTP_CORRELATION_ID, "2-j3r9afaf92j-afkaf"));

        StepVerifier.create(mono)
                    .expectNextMatches(m -> m.correlationId.equals("2-j3r9afaf92j-afkaf") && m.payload.equals(
                            "Hello World!"))
                    .verifyComplete();
    }

    /**
     * Following code counts how many times connection has been established. But there is a bug in the code. Fix it.
     */
    @Test
    public void execution_counter() {
        
        Mono<Void> repeat = Mono.deferContextual(ctx -> {
                    int i = ctx.get(AtomicInteger.class).incrementAndGet();
                    System.out.println("context = " + i);
                    return openConnection();
        })
//                .contextWrite(context -> context.put(AtomicInteger.class, new AtomicInteger(0))) // 덮어쓰기가 되어서 테스트 통과 안됨
                .contextWrite(Context.of(AtomicInteger.class, new AtomicInteger(0))) // 정답
                ;
        /**
         *
         * .contextWrite(context -> context.put(AtomicInteger.class, new AtomicInteger(0)))
         * .contextWrite(Context.of(AtomicInteger.class, new AtomicInteger(0)))
         * 왜 둘의 차이가 생기는지 모르겠음..
         * -> 공식문서 피셜..
         * https://projectreactor.io/docs/core/release/reference/#context
         * https://projectreactor.io/docs/core/release/api/reactor/util/context/Context.html#putAll-reactor.util.context.ContextView-
         *  Use put(Object key, Object value) to store a key-value pair, returning a new Context instance.
         *  You can also merge two contexts into a new one by using putAll(ContextView).
         *  -> put 은 확실히 덮어쓰기가 되는 듯하고, putAll 은 merge 를 수행한다는데 위 현상이 말이 되려면..
         *      기존의 context 에 동일한 Key 가 존재하면 병합 대상 context 를 버리는 방식이어야 한다..
         *      더이상 검색은 안되네..
         */

        StepVerifier.create(repeat.repeat(4))
                    .thenAwait(Duration.ofSeconds(10))
                    .expectAccessibleContext()
                    .assertThat(ctx -> {
                        assert ctx.get(AtomicInteger.class).get() == 5;
                    }).then()
                    .expectComplete().verify();
    }

    /**
     * You need to retrieve 10 result pages from the database.
     * Using the context and repeat operator, keep track of which page you are on.
     * If the error occurs during a page retrieval, log the error message containing page number that has an
     * error and skip the page. Fetch first 10 pages.
     */
    @Test
    public void pagination() {

        AtomicInteger pageWithError = new AtomicInteger(); // set this field when error occurs

        // 문제 원본
//        Flux<Integer> results = getPage(0)
//                .flatMapMany(Page::getResult)
//                .repeat(10)
//                .doOnNext(i -> System.out.println("Received: " + i));

        // 이렇게 하면 되지 않을까 해서 시도해봄..
//        Flux<Integer> results = Mono.deferContextual(contextView -> {
//            int pageNumber = contextView.get(AtomicInteger.class).incrementAndGet();
//            return getPage(pageNumber);
//        }).flatMapMany(Page::getResult)
//                        .doOnError(throwable -> {}) // 에러가 났을 경우 로깅 처리와 pageWithError 에 pageNumber(context) 를 저장 해줘야하는데 doOnError 연산자로는 할 수 없다..

        // 이렇게 하면 되지 않을까 해서 시도해봄.. 2
//        Flux<Integer> results = Mono.create(monoSink -> {
//
//            int pageNumber = monoSink.currentContext().get(AtomicInteger.class).getAndIncrement();
//
//            Flux<Integer> integerFlux = getPage(pageNumber)
//                    .doOnError(throwable -> { // todo 이게 동작하지 않는데.. 이유는?
//                        pageWithError.set(pageNumber);
//                        System.out.println("Error: " + throwable.getMessage() + ", page: " + pageNumber);
//                    })
//                    .onErrorResume(throwable -> Mono.empty()) // empty 를 발행하면 getResult 는 안하는 것인가..? -> 바로 완료 이벤트 방출이라 flatMapMany 도 getResult 를 동작하지 않고 완료처리한다.
//                    .flatMapMany(Page::getResult);
//
//            monoSink.success(integerFlux);
//        })
//                .flatMapMany(o -> (Flux<Integer>) o) // todo, 얘를 Function::identity 로 하면 컴파일 에러가 난다.. 이유는?
//                .repeat(10)
//                .doOnNext(i -> System.out.println("Received: " + i))
//                .contextWrite(Context.of(AtomicInteger.class, new AtomicInteger(0)));

        // 이렇게 하면 되지 않을까 해서 시도해봄.. 3
//        Flux<Integer> results = Flux.deferContextual(contextView -> {
//                    AtomicInteger atomicInteger = contextView.get(AtomicInteger.class);
//                    int pageNumber = atomicInteger.getAndIncrement();
//
//                    return getPage(pageNumber)
//                            .onErrorResume(throwable -> { // todo, 여전히 동작하지 않음.. 이유는?
//                                pageWithError.set(pageNumber);
//                                System.out.println("Error: " + throwable.getMessage() + ", page: " + pageNumber);
//                                return Mono.empty();
//                            })
//                            .flatMapMany(Page::getResult);
//                })
//                .repeat(10)
//                .doOnNext(i -> System.out.println("Received: " + i))
//                .contextWrite(Context.of(AtomicInteger.class, new AtomicInteger(0)));
        /**
         * 위의 todo 에 해당하는 의문점들에 대한 이유.. (해결)
         * -> getPage 를 실행하면 Mono.just(new Page(~~)) 를 실행한다.
         * Mono.just 가 실행되기 전에 new Page 가 실행되고 여기서 Exception 이 발생된다.
         * 따라서, Exception 이 발생하면 Mono.just 로 시작되는 downstream 으로 에러가 전파되는게 아니라..
         * Mono.create, Mono.deferContextual 자체에서 Exception 이 발생한 케이스가 되어서 doOnError, onErrorResume 에서 잡을 수 없고
         * 2 번에서는..
         * .flatMapMany(o -> (Flux<Integer>) o)
         * 3 번에서는..
         * .repeat(10)
         * 바로 위에서 doOnError, onErrorResume 등으로 잡아야 된다.
         * Function::identity 로 할 수 없는 이유도 마찬가지로 위 stream 에서 에러를 잡아놓지 않아서 T 타입이 특정되지 않아 Object 가 되어 직접 캐스팅 해야하는 듯..
         */

        // 정답
        Flux<Integer> results = Mono.deferContextual(contextView -> getPage(contextView.get(AtomicInteger.class).get()))
                .doOnEach(signal -> {
                    if (signal.getType() == SignalType.ON_NEXT) {
                        signal.getContextView().get(AtomicInteger.class).incrementAndGet();
                    } else if (signal.getType() == SignalType.ON_ERROR) {
                        pageWithError.set(signal.getContextView().get(AtomicInteger.class).get());
                        System.out.println("Error has occurred: " + signal.getThrowable().getMessage());
                        System.out.println("Error occurred at page: " + signal.getContextView().get(AtomicInteger.class).getAndIncrement());
                    }
                })
                .onErrorResume(throwable -> Mono.empty())
                .flatMapMany(Page::getResult)
                .repeat(10)
                .doOnNext(i -> System.out.println("Received: " + i))
                .contextWrite(Context.of(AtomicInteger.class, new AtomicInteger(0)));


        //don't change this code
        StepVerifier.create(results)
                    .expectNextCount(90)
                    .verifyComplete();

        Assertions.assertEquals(3, pageWithError.get());
    }
}
