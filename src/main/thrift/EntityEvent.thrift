namespace scala com.gu.entity_indexer

include "entity.thrift"

struct EntityEvent {
    1: required entity.Entity entity;

    2: optional string tagId;
}
