package com.gu.entity_indexer

import java.io.{File, PrintWriter}

import com.gu.contentapi.client.model.v1.TagType.Keyword
import com.gu.contentapi.client.model.{ItemQuery, TagsQuery}
import com.gu.contententity.thrift.entity.organisation.Organisation
import com.gu.contententity.thrift.{Entity, EntityType}
import com.gu.contententity.thrift.entity.person.Person
import com.gu.contententity.thrift.entity.place.Place
import com.gu.entity_indexer.integration.EntityPublisher

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.io.Source

object EntityIndexer extends App {

  val usage = "Usage:\nBy section: <stage> -s <section name>\nBy Ids: <stage> -i <file name>"

  if (args.length < 3) sys.error(usage)

  val config = Config(args(0))

  val entityTypes = config.types.map(t => KnowledgeGraph.americanise(t.name)).toSet //Only these types will be retrieved from Google

  val successWriter = new PrintWriter(new File("./tag-entity-success.csv"))
  val failureWriter = new PrintWriter(new File("./tag-entity-failure.csv"))
  successWriter.write("Name,Type\n")
  failureWriter.write("Name,Suggestions\n")

  args(1) match {
    case "-s" => bySection(args(2))
    case "-i" => byIds(args(2))
    case _ => sys.error(usage)
  }

  def bySection(section: String): Unit = {
    val tagsQuery = TagsQuery().section(section).tagType("keyword").pageSize(10)

    val firstPage = Await.result(config.capi.getResponse(tagsQuery), 20.seconds)
    for (page <- 1 until firstPage.pages) {
      Await.result(config.capi.getResponse(tagsQuery.page(page)) map { response =>
        response.results.foreach { tag =>
          Thread.sleep(500)

          getEntity(tag.webTitle).foreach(entity => EntityPublisher.publish(entity, tag.id)(config))
        }
      }, 20.seconds)
    }
  }

  def byIds(fileName: String): Unit = {
    val file = new File(fileName)
    val ids = Source.fromFile(file).getLines()
    ids.foreach { id =>
      val response = Await.result(config.capi.getResponse(ItemQuery(id)), 20.seconds)
      response.tag.foreach { tag =>
        if (tag.`type` == Keyword) {
          getEntity(tag.webTitle).foreach(entity => EntityPublisher.publish(entity, tag.id)(config))
        } else {
          println(s"Tag $id is not a 'keyword' tag, skipping")
        }
      }
      Thread.sleep(500)
    }
  }

  successWriter.close()
  failureWriter.close()

  config.capi.shutdown

  def getEntity(term: String): Option[Entity] = {
    for {
      googleEntity <- getGoogleEntity(term)
      entity <- buildEntity(googleEntity)
    } yield entity
  }

  def getGoogleEntity(term: String): Option[GoogleEntity] = {
    KnowledgeGraph.getEntities(term, config.googleKey, entityTypes).flatMap { result =>
      /**
        * If there's more than one entity with this name, don't use it.
        * TODO - handle name clashes, e.g. generate id=person/john_smith_2
        */
      val entities = result.itemListElement.collect {
        case element if element.result.name.toLowerCase == term.toLowerCase => element.result
      }

      entities.toList match {
        case entity :: Nil =>
          val toPrint = s"${entity.name},${entity.`@type`.intersect(entityTypes).headOption.getOrElse("MISSING")}\n"
          successWriter.write(toPrint)
          Some(entity)
        case _ =>
          val toPrint = s"$term,${result.itemListElement.map(_.result.name).mkString(",")}\n"
          failureWriter.write(toPrint)
          None
      }
    }
  }

  def buildEntity(googleEntity: GoogleEntity): Option[Entity] = {
    googleEntity.`@type`.intersect(entityTypes).headOption.flatMap {
      case "Person" => Some(Entity(
        id = s"person/${hyphenate(googleEntity.name)}",
        entityType = EntityType.Person,
        googleId = Some(googleEntity.`@id`),
        person = Some(Person(googleEntity.name))
      ))
      case "Place" => Some(Entity(
        id = s"place/${hyphenate(googleEntity.name)}",
        entityType = EntityType.Place,
        googleId = Some(googleEntity.`@id`),
        place = Some(Place(googleEntity.name))
      ))
      case "Organization" => Some(Entity(
        id = s"organisation/${hyphenate(googleEntity.name)}",
        entityType = EntityType.Organisation,
        googleId = Some(googleEntity.`@id`),
        organisation = Some(Organisation(googleEntity.name))
      ))
      case _ =>
        println(s"No valid entity type found for: $googleEntity")
        None
    }
  }

  private def hyphenate(name: String): String = name.toLowerCase.replaceAll(" ", "-")
}
