package org.testcontainers;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class MongoDbExtension implements BeforeAllCallback {

    private final MongoDBContainer mongoDBContainer;
    private final String containerBasePath;
    private final boolean dropCollection;
    private final List<Path> paths;
    private final Consumer<String> logConsumer;
    private Consumer<String> commandConsumer;

    private MongoDbExtension(Builder builder) {
        containerBasePath = builder.containerBasePath;
        dropCollection = builder.dropCollection;
        logConsumer = builder.logConsumer;
        paths = builder.fileResolver.apply(builder.resourcePath, builder.includes, builder.excludes);

        forEachPath(p -> logConsumer.accept("Found: " + p.toString()));
        mongoDBContainer = new MongoDBContainer("mongo:" + builder.mongoDbDockerVersion);
        forEachPath(this::addFileToContainer);

        commandConsumer = command -> executeInContainer(mongoDBContainer, command);
    }

    public static MongoDbExtension standard() {
        return new Builder().build();
    }

    private static void executeInContainer(GenericContainer<?> container, String command) {
        byte[] contentBytes = command.getBytes(UTF_8);
        String shellFilePath = "/etc/mongo-" + UUID.randomUUID();

        container.copyFileToContainer(Transferable.of(contentBytes), shellFilePath);

        try {
            container.execInContainer("/bin/bash", shellFilePath);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void addFileToContainer(Path path) {
        mongoDBContainer.withCopyFileToContainer(
                MountableFile.forClasspathResource(path.toString(), 0744),
                String.format("%s/%s", containerBasePath, path));
    }

    void forEachPath(Consumer<Path> consumer) {
        paths.forEach(consumer::accept);
    }

    void consumeCommand(Consumer<String> consumer) {
        paths.stream()
                .map(path -> buildCommand(path, dropCollection))
                .peek(command -> logConsumer.accept("Command: " + command))
                .forEach(consumer::accept);
    }

    public MongoDBContainer getMongoDbContainer() {
        return mongoDBContainer;
    }

    void setCommandConsumer(Consumer<String> consumer) {
        commandConsumer = consumer;
    }

    // executeInContainer(mongoDBContainer, "mongoimport --db customerprofilerepository --collection inventory --drop --file /docker-entrypoint-initdb.d/inventory2.json");
    private String buildCommand(Path path, boolean drop) {
        return String.format("mongoimport --db %s --collection %s%s --file %s",
                path.getParent().getFileName(), path.getFileName().toString().split("\\.")[0],
                (drop ? " --drop " : ""),
                String.format("%s/%s", containerBasePath, path));
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        consumeCommand(commandConsumer);
    }

    @FunctionalInterface
    public interface TriFunction<A, B, C, O> {
        O apply(A a, B b, C c);

        default <P> TriFunction<A, B, C, P> andThen(Function<? super O, ? extends P> after) {
            Objects.requireNonNull(after);
            return (A a, B b, C c) -> after.apply(apply(a, b, c));
        }
    }

    public static class Builder {
        private final Set<String> includes = new HashSet<>();
        private final Set<String> excludes = new HashSet<>();
        private String resourcePath = "mongodb";
        private String mongoDbDockerVersion = "latest";
        private String containerBasePath = "/docker-entrypoint-initdb.d";
        private boolean dropCollection = true;
        private Consumer<String> logConsumer = data -> {
        };
        private TriFunction<String, Set<String>, Set<String>, List<Path>> fileResolver = defaultFileResolver();

        public Builder resourcePath(String resourcePath) {
            Checks.checkNotNull(resourcePath, "resourcePath");
            Checks.checkArgument(!resourcePath.trim().isEmpty(), "resourcePath must not be blank");
            this.resourcePath = resourcePath;
            return this;
        }

        public Builder mongoDbDockerVersion(String version) {
            Checks.checkNotNull(version, "mongoDbDockerVersion");
            Checks.checkArgument(version.trim().isEmpty(), "mongoDbDockerVersion must not be blank");
            this.mongoDbDockerVersion = version;
            return this;
        }

        public Builder containerBasePath(String containerBasePath) {
            Checks.checkNotNull(containerBasePath, "containerBasePath");
            Checks.checkArgument(containerBasePath.trim().isEmpty(), "containerBasePath must not be blank");
            this.containerBasePath = containerBasePath;
            return this;
        }

        public Builder dropCollections(boolean dropCollection) {
            this.dropCollection = dropCollection;
            return this;
        }

        public Builder logConsumer(Consumer<String> logConsumer) {
            Checks.checkNotNull(logConsumer, "logConsumer");
            this.logConsumer = logConsumer;
            return this;
        }

        public Builder includes(String... regex) {
            Arrays.stream(regex)
                    .filter(r -> r != null && !r.isEmpty())
                    .forEach(includes::add);
            return this;
        }

        public Builder excludes(String... regex) {
            Arrays.stream(regex)
                    .filter(r -> r != null && !r.isEmpty())
                    .forEach(excludes::add);
            return this;
        }

        public Builder fileResolver(TriFunction<String, Set<String>, Set<String>, List<Path>> fileResolver) {
            Checks.checkNotNull(fileResolver, "fileResolver");
            this.fileResolver = fileResolver;
            return this;
        }

        public MongoDbExtension build() {
            try {
                return new MongoDbExtension(this);
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize mongodb junit extension.");
            }
        }

        private TriFunction<String, Set<String>, Set<String>, List<Path>> defaultFileResolver() {
            return (resourceBasePath, includes, excludes) -> new DefaultFileResolver(resourceBasePath, includes, excludes).resolve();
        }
    }

    private static class DefaultFileResolver {
        private final String resourcePath;
        private final Set<String> includes;
        private final Set<String> excludes;

        DefaultFileResolver(String resourcePath, Set<String> includes, Set<String> excludes) {
            this.resourcePath = resourcePath;
            this.includes = includes;
            this.excludes = excludes;
        }

        static Path toPath(String path) throws URISyntaxException {
            URL resource = MongoDbExtension.class.getResource(path.startsWith("/") ? path : "/" + path);
            if (resource == null) {
                throw new IllegalArgumentException("file not found!");
            } else {
                return new File(resource.toURI()).toPath();
            }
        }

        static List<Path> resolveAll(Path path) throws Exception {
            return Files.walk(path)
                    .map(Path::toFile)
                    .filter(File::isFile)
                    .map(f -> path.getParent().relativize(f.toPath()))
                    .collect(Collectors.toList());
        }

        public List<Path> resolve() {
            try {
                return resolveAll(toPath(resourcePath)).stream()
                        .filter(this::isIncluded)
                        .filter(this::isNotExcluded)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }

        private boolean isIncluded(Path path) {
            String filepath = path.getFileName().toString();
            return includes.isEmpty() || includes.stream().anyMatch(filepath::matches);
        }

        private boolean isNotExcluded(Path path) {
            String filepath = path.getFileName().toString();
            return excludes.isEmpty() || excludes.stream().noneMatch(filepath::matches);
        }
    }
}
