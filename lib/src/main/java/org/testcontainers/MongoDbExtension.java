package org.testcontainers;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
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
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * root/db_name/collection_name.json
 * <p>
 * TODO: add include/exclude rules
 */
public class MongoDbExtension implements BeforeAllCallback {

    private final MongoDBContainer mongoDBContainer;
    private final String resourcePath;
    private final String containerBasePath;
    private final boolean dropCollection;
    private final List<Path> paths;
    private final Set<String> includes;
    private final Set<String> excludes;
    private final Consumer<String> logConsumer;

    private MongoDbExtension(Builder builder) {
        resourcePath = builder.resourcePath;
        containerBasePath = builder.containerBasePath;
        dropCollection = builder.dropCollection;
        includes = builder.includes;
        excludes = builder.excludes;
        logConsumer = builder.logConsumer;
        paths = resolveFileResolver(builder.fileResolver).resolve();

        paths.forEach(p -> logConsumer.accept("Found: " + p.toString()));
        MongoDBContainer container = new MongoDBContainer("mongo:" + builder.mongoDbDockerVersion);
        paths.forEach(p -> container.withCopyFileToContainer(
                MountableFile.forClasspathResource(p.toString(), 0744),
                String.format("%s/%s", containerBasePath, p)));

        this.mongoDBContainer = container;
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

    private FileResolver resolveFileResolver(FileResolver fileResolver) {
        return fileResolver == null ? new DefaultFileResolver(this) : fileResolver;
    }

    public void executeInContainer() {
        paths.stream()
                .map(path -> buildCommand(path, dropCollection))
                .peek(command -> logConsumer.accept("Command: " + command))
                .forEach(command-> executeInContainer(mongoDBContainer, command));
    }

    public MongoDBContainer getMongoDbContainer() {
        return mongoDBContainer;
    }

    // executeInContainer(mongoDBContainer, "mongoimport --db customerprofilerepository --collection inventory --drop --file /docker-entrypoint-initdb.d/inventory2.json");
    private String buildCommand(Path path, boolean drop) {
        return String.format("mongoimport --db %s --collection %s%s --file %s",
                path.getParent().getFileName(), path.getFileName().toString().split("\\.")[0],
                (drop ? " --drop " : ""),
                String.format("%s/%s", containerBasePath, path));
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        executeInContainer();
    }

    public interface FileResolver {
        List<Path> resolve();
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
        private FileResolver fileResolver;

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
                    .forEach(r -> includes.add(r));
            return this;
        }

        public Builder excludes(String... regex) {
            Arrays.stream(regex)
                    .filter(r -> r != null && !r.isEmpty())
                    .forEach(r -> excludes.add(r));
            return this;
        }

        public Builder fileResolver(FileResolver fileResolver) {
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
    }

    private static class DefaultFileResolver implements FileResolver {
        private final String resourcePath;
        private final Set<String> includes;
        private final Set<String> excludes;

        DefaultFileResolver(MongoDbExtension extension) {
            resourcePath = extension.resourcePath;
            includes = extension.includes;
            excludes = extension.excludes;
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

        @Override
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
