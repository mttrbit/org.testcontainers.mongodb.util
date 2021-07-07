# mttrbit.testcontainers.mongodb-util
Junit5 extension for seeding a MongoDB testcontainer with data.

# Introduction

Testcontainer MongoDB Util is a Junit5 extension for populating data into a single or multiple MongoDB instances. The extension is used to write integration tests and out-of-process component tests and provides you an ability to run your tests with the docker image of MongoDB (by the use of Testcontainers).

Also, tescontainer-mongodb-util allows you to write tests in a more pragmatic manner by importing your data sets (defined as JSON files) into the docker image directly. You can use it to prepare the state of a database prior to executing the tests and/or to check the state after some activities (update, delete, etc).

# Getting started

**NOTE** At the moment you need to build it locally and publish the jar to your local maven repository.

You need to add the dependency (using **maven**)
```xml
<dependency>
    <groupId>mttrbit.testcontainers</groupId>
    <artifactId>mongodb-util</artifactId>
    <version>0.1</version>
    <scope>test</scope>
</dependency>
```

or (using **gradle**)
```
testImplementation 'mttrbit.testcontainers:mongodb-util:0.1'
```

# Usage

## JUnit5 integration test

```java
@Testcontainers(disabledWithoutDocker = true)
class MongoDbExtensionTest {

    // loads files located in testResources/mongodb
    // creates a database with the name inventorydb
    // creates three collections: anotherCollection, collection, inventory
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
}
```

## Spring Boot + JUnit5
```java
@Testcontainers(disabledWithoutDocker = true)
@DataMongoTest(excludeAutoConfiguration = EmbeddedMongoAutoConfiguration.class)
class CustomerProfileRepositoryTest {

    @RegisterExtension
    static MongoDbExtension mongoDbExtension = MongoDbExtension.standard();

    @Container
    static MongoDBContainer mongoDBContainer = mongoDbExtension.getMongoDbContainer();

    @Autowired
    MongoTemplate mongoTemplate;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Test
    public void test() {
        // some tests
    }
}
```
