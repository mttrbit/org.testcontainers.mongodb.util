package org.testcontainers;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class MongoDbExtensionFilteringTest {

    @RegisterExtension
    static MongoDbExtension mongoExtInclude = new MongoDbExtension.Builder().includes("inventory.json").build();

    @RegisterExtension
    static MongoDbExtension mongoExtExclude = new MongoDbExtension.Builder().excludes("inventory.json").build();

    @RegisterExtension
    static MongoDbExtension mongoComb1 = new MongoDbExtension.Builder()
            .includes(".*json$")
            .excludes("anotherCollection.json")
            .logConsumer(System.out::println).build();

    @Container
    static MongoDBContainer mongoInclude = mongoExtInclude.getMongoDbContainer();

    @Container
    static MongoDBContainer mongoExclude = mongoExtExclude.getMongoDbContainer();

    @Container
    static MongoDBContainer mongoContComb1 = mongoComb1.getMongoDbContainer();

    private static MongoClient clientInclude;
    private static MongoClient clientExclude;
    private static MongoClient clientComb1;

    @BeforeAll
    public static void init() {
        clientInclude = new MongoClient(new MongoClientURI(mongoInclude.getReplicaSetUrl()));
        clientExclude = new MongoClient(new MongoClientURI(mongoExclude.getReplicaSetUrl()));
        clientComb1 = new MongoClient(new MongoClientURI(mongoContComb1.getReplicaSetUrl()));
    }

    @Test
    public void should_include_single_collection_via_inclusion() {
        Iterable<String> it = clientInclude.getDatabase("inventorydb").listCollectionNames();
        assertEquals(1, StreamSupport.stream(it.spliterator(), false).count());
        assertTrue(StreamSupport.stream(it.spliterator(), false).anyMatch(coll -> coll.equals("inventory")));
    }

    @Test
    public void should_include_collections_via_exclusion() {
        Iterable<String> it = clientExclude.getDatabase("inventorydb").listCollectionNames();
        assertEquals(2, StreamSupport.stream(it.spliterator(), false).count());
        assertTrue(StreamSupport.stream(it.spliterator(), false).anyMatch(coll -> coll.equals("collection")));
        assertTrue(StreamSupport.stream(it.spliterator(), false).anyMatch(coll -> coll.equals("anotherCollection")));
    }

    @Test
    public void should_include_collections_via_inclusion_exclusion() {
        Iterable<String> it = clientComb1.getDatabase("inventorydb").listCollectionNames();
        assertEquals(2, StreamSupport.stream(it.spliterator(), false).count());
        assertTrue(StreamSupport.stream(it.spliterator(), false).anyMatch(coll -> coll.equals("collection")));
        assertTrue(StreamSupport.stream(it.spliterator(), false).anyMatch(coll -> coll.equals("inventory")));
    }
}