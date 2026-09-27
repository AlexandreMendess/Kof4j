[English](25-spring.md) | [Português](25-spring.pt_BR.md)

# 25 — Spring

> **Status: future (post 0.5.0-beta — `kof.web` + `kof_db` already cover the case without Spring)**
>
> Spring integration is one of Kof's long-term goals. This chapter documents the planned vision — and what works today without Spring.

## What works today (without Spring)

Complete web APIs run with `kof.web` (native stack, no container) +
`kof.db`/`kof.orm` for persistence:

```kf
record User(String name, Int age)

main() {
    var app = web.app()
    app.use {                          // middleware
        if (header("x-auth") == "secret") {
            return null
        }
        return "{\"error\": \"unauthorized\"}"
    }
    app.get("/users/:id") {
        return "user " + param("id") + " q=" + query("name")
    }
    app.post("/user") {
        var user = json.decode<User>(body())
        return status(201, json.encode(user))
    }
    app.ws("/chat") {                  // WebSocket (30/08)
        var m = wsMessage()
        wsSend("echo: " + m)
    }
    app.sse("/events") {               // SSE (30/08)
        sse.send("hello")
        sse.event("tick", "hello")
        sse.close()
    }
    app.listen(8080)
}
```

- Routes `get/post/put/delete/patch/options` + `ws` + `sse`, path params
  (`:id`), `query()`, `header()`, `body()`, `method()`, `path()`;
- Rich response: `status(201, body)` + `headerSet("X", "y")`;
- HTTP client: `http.get/post/put/delete/patch/options` + `timeout`/
  `retry`/`circuit` (JVM+JS; Native reports `HTTP002`);
- Web: `WEB002` on Native (no server) — the web stack is JVM today.

See `docs/stdlib/stdlib-web.md`.

## The long-term vision: the goal

Use real Spring Boot, not a "Kof Spring".

```kf
@SpringBootApplication
class Application {
    static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## REST Controller

```kf
@RestController
class UserController(UserService service) {

    @GetMapping("/users/{id}")
    User find(@PathVariable UUID id) {
        return service.find(id);
    }

    @GetMapping("/users")
    List<User> findAll() {
        return service.findAll();
    }

    @PostMapping("/users")
    User create(@RequestBody CreateUserRequest request) {
        return service.create(request);
    }
}
```

## Service

```kf
@Service
class UserService(UserRepository repository) {

    User find(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new UserNotFound(id.toString()));
    }

    List<User> findAll() {
        return repository.findAll();
    }
}
```

## Repository

```kf
interface UserRepository extends CrudRepository<User, UUID> {
    List<User> findByActiveTrue();
}
```

## How it works

1. Kof generates standard JVM bytecode
2. Spring sees the annotations in the bytecode
3. Spring creates proxies normally
4. Dependency injection works
5. AOP works
6. Transaction management works

Kof does not need a special module for Spring. The bytecode is Java.

## Configuration

```kf
@Configuration
class AppConfig {
    @Bean
    DataSource dataSource() {
        return new HikariDataSource();
    }
}
```

## Testing with Spring

```kf
@SpringBootTest
class UserServiceTest {
    @Autowired
    UserService service;

    @Test
    void deveEncontrarUser() {
        var user = service.find(UUID.randomUUID());
        assertNotNull(user);
    }
}
```

## Next step

[Real-World Application →](26-real-world-application.md)
