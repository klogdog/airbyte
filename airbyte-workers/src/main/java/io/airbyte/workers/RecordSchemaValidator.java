/*
 * Copyright (c) 2021 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.workers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.airbyte.protocol.models.AirbyteRecordMessage;
import io.airbyte.validation.json.JsonSchemaValidator;
import io.airbyte.validation.json.JsonValidationException;
import io.airbyte.workers.exception.RecordSchemaValidationException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates that AirbyteRecordMessage data conforms to the JSON schema defined by the source's
 * configured catalog
 */

public class RecordSchemaValidator {

  private final Map<String, JsonNode> streams;
  private static final Logger LOGGER = LoggerFactory.getLogger(RecordSchemaValidator.class);

  public RecordSchemaValidator(final Map<String, JsonNode> streamNamesToSchemas) {
    // streams is Map of a stream source namespace + name mapped to the stream schema
    // for easy access when we check each record's schema
    this.streams = streamNamesToSchemas;
  }

  /**
   * Takes an AirbyteRecordMessage and uses the JsonSchemaValidator to validate that its data conforms
   * to the stream's schema If it does not, this method throws a RecordSchemaValidationException
   *
   * @param message
   * @throws RecordSchemaValidationException
   */
  public void validateSchema(final AirbyteRecordMessage message) throws RecordSchemaValidationException {
    // the stream this message corresponds to, including the stream namespace
    final String messageStream = String.format("%s" + message.getStream(), Objects.toString(message.getNamespace(), ""));
    final JsonNode messageData = message.getData();
    final JsonNode matchingSchema = streams.get(messageStream);

    final JsonSchemaValidator validator = new JsonSchemaValidator();

    // We must choose a JSON validator version for validating the schema
    // Rather than allowing connectors to use any version, we enforce validation using V7
    ((ObjectNode) matchingSchema).put("$schema", "http://json-schema.org/draft-07/schema#");

    try {
      validator.ensure(matchingSchema, messageData);
    } catch (final JsonValidationException e) {
      final List<String[]> invalidRecordDataAndType = validator.getValidationMessageArgs(matchingSchema, messageData);
      final List<String> invalidFields = validator.getValidationMessagePaths(matchingSchema, messageData);

      final AtomicReference<String> validationMessagesToDisplay = new AtomicReference<>("");
      for (int i = 0; i < invalidFields.size(); i++) {
        final String newMessage = String.format("Expected %s to be %s. ", invalidFields.get(i), invalidRecordDataAndType.get(i)[1]);
        validationMessagesToDisplay.set(validationMessagesToDisplay + newMessage);
      }

      final String streamWithNamespace = message.getNamespace() == null ? message.getStream() : message.getNamespace() + "-" + message.getStream();
      throw new RecordSchemaValidationException(streamWithNamespace,
          String.format("Record schema validation failed for %s. %s", streamWithNamespace, validationMessagesToDisplay));
    }
  }

}
