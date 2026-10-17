# xoom-actors-toolbox

Compile-time proxy generation for [xoom-actors](https://github.com/vlingo/xoom-actors), plus the
three calls that start, use and stop an actor system.

Nothing in xoom-actors changes. Its runtime already looks for a `<Protocol>__Proxy` class before it
compiles one with javac while your application runs; the toolbox provides that class while your
project is built, and wires the `World` up so no `xoom-actors.properties` is needed.

## Quickstart

Two dependencies:

```xml
<dependency>
  <groupId>io.vlingo.xoom</groupId>
  <artifactId>xoom-actors</artifactId>
  <version>1.11.1</version>
</dependency>
<dependency>
  <groupId>io.vlingo.xoom</groupId>
  <artifactId>xoom-actors-toolbox</artifactId>
  <version>0.1.0</version>
</dependency>
```

All the code:

```java
@ActorProtocol
public interface Hello {
  Completes<String> hello(String name);
}

public class HelloActor extends Actor implements Hello {
  @Override
  public Completes<String> hello(final String name) {
    return completes().with("Hello, " + name + "!");
  }
}

public static void main(final String[] args) {
  final World world = Actors.world("hello");
  final Hello hello = Actors.actor(world, Hello.class, HelloActor.class, HelloActor::new);

  final String greeting = hello.hello("World").await();
  System.out.println(greeting);   // Hello, World!

  Actors.shutdown(world);
  System.exit(0);   // a console app ends explicitly; see the notes
}
```

Run the example, which is exactly this: `mvn install && mvn -f examples/hello compile exec:java`

## Notes

* **One line of plumbing stays**, and only for actors that send messages to themselves:
  `private final Hello self = selfAs(Hello.class);`
* **Protocol methods** return `void`, `Completes<T>`, `Future<T>` or `CompletableFuture<T>`; anything
  else fails the build with a message that names the method. `@SafeProxyGenerable` protocols keep
  working as before.
* **Nothing to configure.** The annotation processor comes on the classpath with the dependency.
* **A console application must end explicitly:** `World.terminate()` does not stop the plugin-scanner
  thread that `World`'s constructor starts (`World.java:665`), so the JVM stays alive. Server
  applications are unaffected.
* **Native image:** `META-INF/native-image/reflect-config.json` is written for every generated proxy.
* **Lombok is not needed here.** A Java annotation processor cannot rewrite the class it is given —
  Lombok does that by patching javac internals. For immutable message types, Lombok (or records) is
  still a good idea; that is your choice, not a dependency of this toolbox.

## Publishing

```bash
mvn -B deploy -s settings.xml      # GitHub Packages, see .github/workflows/publish.yml
```

Set `<github.repository>` in `pom.xml` to the `<owner>/<repo>` you publish to.

## Building

```bash
mvn test         # proves the runtime uses the generated proxy, and that nothing is compiled at runtime
mvn install      # then: mvn -f examples/hello compile exec:java
```

## License

Mozilla Public License, v. 2.0. See [LICENSE](LICENSE).
