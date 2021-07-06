package org.testcontainers;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class MongoDbExtensionTest {

    @RegisterExtension
    static MongoDbExtension mongoDbExtension = MongoDbExtension.standard();

    @Container
    static MongoDBContainer mongoDBContainer = mongoDbExtension.getMongoDbContainer();

    private static MongoClient mongoClient;

    @BeforeAll
    public static void init() {
        mongoClient = new MongoClient(new MongoClientURI(mongoDBContainer.getReplicaSetUrl()));
    }

    @Test
    public void should_create_database() {
        Stream<String> databases = StreamSupport.stream(mongoClient.listDatabaseNames().spliterator(), false);
        assertTrue(databases.anyMatch(db -> db.equals("inventorydb")));
    }

    @Test
    public void should_create_collection() {
        Stream<String> collections = StreamSupport.stream(mongoClient.getDatabase("inventorydb").listCollectionNames().spliterator(), false);
        assertTrue(collections.anyMatch(db -> db.equals("inventory")));
        assertEquals(5, mongoClient.getDatabase("inventorydb").getCollection("inventory").countDocuments());
    }
}