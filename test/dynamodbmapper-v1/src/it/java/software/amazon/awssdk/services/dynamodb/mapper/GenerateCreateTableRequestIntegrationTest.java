/*
 * Copyright 2010-2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.services.dynamodb.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import software.amazon.awssdk.services.dynamodb.datamodeling.DynamoDbMapper;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndexDescription;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.LocalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.LocalSecondaryIndexDescription;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.services.dynamodb.util.TableUtils;
import software.amazon.awssdk.test.util.UnorderedCollectionComparator;
import software.amazon.awssdk.util.ImmutableObjectUtils;
import utils.test.util.DynamoDBTestBase;

/**
 * Tests that the CreateTableRequest generated by DynamoDBMapper.generateCreateTableRequest
 * correctly creates the expected table.
 */
public class GenerateCreateTableRequestIntegrationTest extends DynamoDBTestBase {

    private static final ProvisionedThroughput DEFAULT_CAPACITY = ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build();
    private static DynamoDbMapper mapper;
    private static Set<String> testedTableName = new HashSet<>();

    @BeforeClass
    public static void setUp() throws Exception {
        DynamoDBTestBase.setUpTestBase();
        mapper = new DynamoDbMapper(dynamo);
    }

    @AfterClass
    public static void tearDown() {
        for (String tableName : testedTableName) {
            dynamo.deleteTable(DeleteTableRequest.builder().tableName(tableName).build());
        }
    }

    private static void setProvisionedThroughput(CreateTableRequest request, ProvisionedThroughput throughput) {
        ImmutableObjectUtils.setObjectMember(request, "provisionedThroughput", throughput);
        //request.setProvisionedThroughput(throughput);
        if (request.globalSecondaryIndexes() != null) {
            for (GlobalSecondaryIndex gsi : request.globalSecondaryIndexes()) {
                ImmutableObjectUtils.setObjectMember(gsi, "provisionedThroughput", throughput);
                //gsi.setProvisionedThroughput(throughput);
            }
        }
    }

    private static boolean equalLsi(Collection<LocalSecondaryIndex> a, Collection<LocalSecondaryIndexDescription> b) {
        return UnorderedCollectionComparator.equalUnorderedCollections(a, b, new LocalSecondaryIndexDefinitionComparator());
    }

    private static boolean equalGsi(Collection<GlobalSecondaryIndex> a, Collection<GlobalSecondaryIndexDescription> b) {
        return UnorderedCollectionComparator.equalUnorderedCollections(a, b, new GlobalSecondaryIndexDefinitionComparator());
    }

    private static String appendCurrentTimeToTableName(CreateTableRequest request) {
        String appendedName = String.format("%s-%d", request.tableName(), System.currentTimeMillis());
        ImmutableObjectUtils.setObjectMember(request, "tableName", appendedName);
        /// /request.setTableName(appendedName);
        return appendedName;
    }

    @Test
    public void testParseIndexRangeKeyClass() throws Exception {
        CreateTableRequest request = mapper.generateCreateTableRequest(IndexRangeKeyClass.class);
        String createdTableName = appendCurrentTimeToTableName(request);
        testedTableName.add(createdTableName);
        setProvisionedThroughput(request, DEFAULT_CAPACITY);

        TableDescription createdTableDescription = dynamo.createTable(request).tableDescription();

        assertEquals(createdTableName, createdTableDescription.tableName());
        List<KeySchemaElement> expectedKeyElements = Arrays.asList(
                KeySchemaElement.builder().attributeName("key").keyType(KeyType.HASH).build(),
                KeySchemaElement.builder().attributeName("rangeKey").keyType(KeyType.RANGE).build()
        );
        assertEquals(expectedKeyElements, createdTableDescription.keySchema());

        List<AttributeDefinition> expectedAttrDefinitions = Arrays.asList(
                AttributeDefinition.builder().attributeName("key").attributeType(ScalarAttributeType.N).build(),
                AttributeDefinition.builder().attributeName("rangeKey").attributeType(ScalarAttributeType.N).build(),
                AttributeDefinition.builder().attributeName("indexFooRangeKey").attributeType(ScalarAttributeType.N).build(),
                AttributeDefinition.builder().attributeName("indexBarRangeKey").attributeType(ScalarAttributeType.N).build(),
                AttributeDefinition.builder().attributeName("multipleIndexRangeKey").attributeType(ScalarAttributeType.N).build()
        );
        assertTrue(UnorderedCollectionComparator.equalUnorderedCollections(
                expectedAttrDefinitions,
                createdTableDescription.attributeDefinitions()));

        List<LocalSecondaryIndex> expectedLsi = Arrays.asList(
                LocalSecondaryIndex.builder()
                                   .indexName("index_foo")
                                   .keySchema(
                                           KeySchemaElement.builder().attributeName("key").keyType(KeyType.HASH).build(),
                                           KeySchemaElement.builder().attributeName("indexFooRangeKey").keyType(KeyType.RANGE).build()).build(),
                LocalSecondaryIndex.builder()
                                   .indexName("index_bar")
                                   .keySchema(
                                           KeySchemaElement.builder().attributeName("key").keyType(KeyType.HASH).build(),
                                           KeySchemaElement.builder().attributeName("indexBarRangeKey").keyType(KeyType.RANGE).build()).build(),
                LocalSecondaryIndex.builder()
                                   .indexName("index_foo_copy")
                                   .keySchema(
                                           KeySchemaElement.builder().attributeName("key").keyType(KeyType.HASH).build(),
                                           KeySchemaElement.builder().attributeName("multipleIndexRangeKey").keyType(KeyType.RANGE).build()).build(),
                LocalSecondaryIndex.builder()
                                   .indexName("index_bar_copy")
                                   .keySchema(
                                           KeySchemaElement.builder().attributeName("key").keyType(KeyType.HASH).build(),
                                           KeySchemaElement.builder().attributeName("multipleIndexRangeKey").keyType(KeyType.RANGE).build()).build());
        assertTrue(equalLsi(expectedLsi, createdTableDescription.localSecondaryIndexes()));

        assertNull(request.globalSecondaryIndexes());
        assertEquals(DEFAULT_CAPACITY, request.provisionedThroughput());

        // Only one table with indexes can be created simultaneously
        TableUtils.waitUntilActive(dynamo, createdTableName);
    }

    @Test
    public void testComplexIndexedHashRangeClass() throws Exception {
        CreateTableRequest request = mapper.generateCreateTableRequest(MapperQueryExpressionTest.HashRangeClass.class);
        String createdTableName = appendCurrentTimeToTableName(request);
        testedTableName.add(createdTableName);
        setProvisionedThroughput(request, DEFAULT_CAPACITY);

        TableDescription createdTableDescription = dynamo.createTable(request).tableDescription();

        assertEquals(createdTableName, createdTableDescription.tableName());
        List<KeySchemaElement> expectedKeyElements = Arrays.asList(
                KeySchemaElement.builder().attributeName("primaryHashKey").keyType(KeyType.HASH).build(),
                KeySchemaElement.builder().attributeName("primaryRangeKey").keyType(KeyType.RANGE).build()
        );
        assertEquals(expectedKeyElements, createdTableDescription.keySchema());

        List<AttributeDefinition> expectedAttrDefinitions = Arrays.asList(
                AttributeDefinition.builder().attributeName("primaryHashKey").attributeType(ScalarAttributeType.S).build(),
                AttributeDefinition.builder().attributeName("indexHashKey").attributeType(ScalarAttributeType.S).build(),
                AttributeDefinition.builder().attributeName("primaryRangeKey").attributeType(ScalarAttributeType.S).build(),
                AttributeDefinition.builder().attributeName("indexRangeKey").attributeType(ScalarAttributeType.S).build(),
                AttributeDefinition.builder().attributeName("anotherIndexRangeKey").attributeType(ScalarAttributeType.S).build()
        );
        assertTrue(UnorderedCollectionComparator.equalUnorderedCollections(
                expectedAttrDefinitions,
                createdTableDescription.attributeDefinitions()));

        List<LocalSecondaryIndex> expectedLsi = Arrays.asList(
                LocalSecondaryIndex.builder()
                                   .indexName("LSI-primary-range")
                                   .keySchema(
                                           KeySchemaElement.builder().attributeName("primaryHashKey").keyType(KeyType.HASH).build(),
                                           KeySchemaElement.builder().attributeName("primaryRangeKey").keyType(KeyType.RANGE).build()).build(),
                LocalSecondaryIndex.builder()
                                   .indexName("LSI-index-range-1")
                                   .keySchema(
                                           KeySchemaElement.builder().attributeName("primaryHashKey").keyType(KeyType.HASH).build(),
                                           KeySchemaElement.builder().attributeName("indexRangeKey").keyType(KeyType.RANGE).build()).build(),
                LocalSecondaryIndex.builder()
                                   .indexName("LSI-index-range-2")
                                   .keySchema(
                                           KeySchemaElement.builder().attributeName("primaryHashKey").keyType(KeyType.HASH).build(),
                                           KeySchemaElement.builder().attributeName("indexRangeKey").keyType(KeyType.RANGE).build()).build(),
                LocalSecondaryIndex.builder()
                                   .indexName("LSI-index-range-3")
                                   .keySchema(
                                           KeySchemaElement.builder().attributeName("primaryHashKey").keyType(KeyType.HASH).build(),
                                           KeySchemaElement.builder().attributeName("anotherIndexRangeKey").keyType(KeyType.RANGE).build()).build());
        assertTrue(equalLsi(expectedLsi, createdTableDescription.localSecondaryIndexes()));

        List<GlobalSecondaryIndex> expectedGsi = Arrays.asList(
                GlobalSecondaryIndex.builder()
                                    .indexName("GSI-primary-hash-index-range-1")
                                    .keySchema(
                                            KeySchemaElement.builder().attributeName("primaryHashKey").keyType(KeyType.HASH).build(),
                                            KeySchemaElement.builder().attributeName("indexRangeKey").keyType(KeyType.RANGE).build()).build(),
                GlobalSecondaryIndex.builder()
                                    .indexName("GSI-primary-hash-index-range-2")
                                    .keySchema(
                                            KeySchemaElement.builder().attributeName("primaryHashKey").keyType(KeyType.HASH).build(),
                                            KeySchemaElement.builder().attributeName("anotherIndexRangeKey").keyType(KeyType.RANGE).build()).build(),
                GlobalSecondaryIndex.builder()
                                    .indexName("GSI-index-hash-primary-range")
                                    .keySchema(
                                            KeySchemaElement.builder().attributeName("indexHashKey").keyType(KeyType.HASH).build(),
                                            KeySchemaElement.builder().attributeName("primaryRangeKey").keyType(KeyType.RANGE).build()).build(),
                GlobalSecondaryIndex.builder()
                                    .indexName("GSI-index-hash-index-range-1")
                                    .keySchema(
                                            KeySchemaElement.builder().attributeName("indexHashKey").keyType(KeyType.HASH).build(),
                                            KeySchemaElement.builder().attributeName("indexRangeKey").keyType(KeyType.RANGE).build()).build(),
                GlobalSecondaryIndex.builder()
                                    .indexName("GSI-index-hash-index-range-2")
                                    .keySchema(
                                            KeySchemaElement.builder().attributeName("indexHashKey").keyType(KeyType.HASH).build(),
                                            KeySchemaElement.builder().attributeName("indexRangeKey").keyType(KeyType.RANGE).build()).build());
        assertTrue(equalGsi(expectedGsi, createdTableDescription.globalSecondaryIndexes()));

        assertEquals(DEFAULT_CAPACITY, request.provisionedThroughput());

        // Only one table with indexes can be created simultaneously
        TableUtils.waitUntilActive(dynamo, createdTableName);
    }

    private static class LocalSecondaryIndexDefinitionComparator
            implements
            UnorderedCollectionComparator.CrossTypeComparator<LocalSecondaryIndex, LocalSecondaryIndexDescription> {

        @Override
        public boolean equals(LocalSecondaryIndex a, LocalSecondaryIndexDescription b) {
            return a.indexName().equals(b.indexName())
                   && a.keySchema().equals(b.keySchema());
        }

    }

    private static class GlobalSecondaryIndexDefinitionComparator
            implements
            UnorderedCollectionComparator.CrossTypeComparator<GlobalSecondaryIndex, GlobalSecondaryIndexDescription> {

        @Override
        public boolean equals(GlobalSecondaryIndex a, GlobalSecondaryIndexDescription b) {
            return a.indexName().equals(b.indexName())
                   && a.keySchema().equals(b.keySchema());
        }
    }
}