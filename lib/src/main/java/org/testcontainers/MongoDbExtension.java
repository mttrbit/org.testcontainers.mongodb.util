package org.testcontainers;

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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * root/db_name/collection_name.json
 *
 * TODO: add include/exclude rules
 */
public class MongoDbExtension implements TestInstancePostProcessor {

    private final MongoDBContainer mongoDBContainer;
    private final String containerBasePath;
    private final boolean dropCollection;
    private final List<Path> paths;

    public static MongoDbExtension standard() {
        return new Builder().build();
    }

    private MongoDbExtension(Builder builder) throws Exception {
        containerBasePath = builder.containerBasePath;
        dropCollection = builder.dropCollection;
        paths = resolveAll(toPath(builder.resourcePath));

        MongoDBContainer container = new MongoDBContainer("mongo:" + builder.mongoDbDockerVersion);
        paths.forEach(p -> container.withCopyFileToContainer(
                MountableFile.forClasspathResource(p.toString(), 0744),
                String.format("%s/%s", containerBasePath, p)));

        this.mongoDBContainer = container;
    }

    private static List<Path> resolveAll(Path path) throws Exception {
        return Files.walk(path)
                .map(Path::toFile)
                .filter(File::isFile)
                .map(f -> path.getParent().relativize(f.toPath()))
                .collect(Collectors.toList());
    }

    static Path toPath(String path) throws URISyntaxException {
        URL resource = MongoDbExtension.class.getResource(path.startsWith("/") ? path : "/" + path);
        if (resource == null) {
            throw new IllegalArgumentException("file not found!");
        } else {
            return new File(resource.toURI()).toPath();
        }
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

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) {
        paths.forEach(p -> executeInContainer(mongoDBContainer, buildCommand(p, dropCollection)));
    }

    public MongoDBContainer getMongoDbContainer() {
        return mongoDBContainer;
    }

//        executeInContainer(mongoDBContainer, "mongoimport --db customerprofilerepository --collection inventory --drop --file /docker-entrypoint-initdb.d/inventory2.json");

    private String buildCommand(Path path, boolean drop) {
        return String.format("mongoimport --db %s --collection %s%s --file %s",
                path.getParent().getFileName(), path.getFileName().toString().split("\\.")[0],
                (drop ? " --drop " : ""),
                String.format("%s/%s", containerBasePath, path));
    }

    public static class Builder {
        private String resourcePath = "mongodb";
        private String mongoDbDockerVersion = "latest";
        private String containerBasePath = "/docker-entrypoint-initdb.d";
        private boolean dropCollection = true;

        public Builder withResourcePath(String resourcePath) {
            Checks.checkNotNull(resourcePath, "resourcePath");
            Checks.checkArgument(!resourcePath.trim().isEmpty(), "resourcePath must not be blank");
            this.resourcePath = resourcePath;
            return this;
        }

        public Builder withMongoDbDockerVersion(String version) {
            Checks.checkNotNull(version, "mongoDbDockerVersion");
            Checks.checkArgument(version.trim().isEmpty(), "mongoDbDockerVersion must not be blank");
            this.mongoDbDockerVersion = version;
            return this;
        }

        public Builder withContainerBasePath(String containerBasePath) {
            Checks.checkNotNull(containerBasePath, "containerBasePath");
            Checks.checkArgument(containerBasePath.trim().isEmpty(), "containerBasePath must not be blank");
            this.containerBasePath = containerBasePath;
            return this;
        }

        public Builder withDropCollection(boolean dropCollection) {
            this.dropCollection = dropCollection;
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
}
