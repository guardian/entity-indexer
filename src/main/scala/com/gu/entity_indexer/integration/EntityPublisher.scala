package com.gu.entity_indexer.integration

import com.amazonaws.services.kinesis.model.PutRecordRequest
import com.gu.contententity.thrift.Entity
import com.gu.entity_indexer.{Config, EntityEvent}

import scala.util.{Failure, Success, Try}

object EntityPublisher {
  def publish(entity: Entity, tagId: String)(config: Config) = {
    val event = EntityEvent(entity, Some(tagId))
    val data = ThriftSerializer.serializeEvent(event)

    val record = new PutRecordRequest()
      .withData(data)
      .withStreamName(config.kinesis.streamName)
      .withPartitionKey(event.entity.id)

    Try(config.kinesis.kinesisClient.putRecord(record)) match {
      case Success(_) => println(s"Publishing entity with id: ${event.entity.id}")
      case Failure(error) => println(s"Failed to publish entity with id: ${event.entity.id}: ${error.getMessage}", error)
    }
  }
}
