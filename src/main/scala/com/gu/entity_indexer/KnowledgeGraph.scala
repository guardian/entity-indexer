package com.gu.entity_indexer

import com.gu.contententity.thrift.EntityType
import okhttp3.{OkHttpClient, Request}
import io.circe.parser.decode
import io.circe.generic.auto._

case class GoogleResponse(itemListElement: Seq[ItemListElement])
case class ItemListElement(result: GoogleEntity, resultScore: Double)
case class GoogleEntity(name: String, `@type`: Set[String], description: Option[String], `@id`: String)

object KnowledgeGraph {
  private val httpClient: OkHttpClient = new OkHttpClient()

  private def buildUrl(term: String, key: String, types: Set[String]): String = {
    val typesParams = types.map(t => s"types=${americanise(t)}").mkString("&")
    s"""https://kgsearch.googleapis.com/v1/entities:search?query=${term.replaceAll(" ","+")}&key=$key&limit=5&$typesParams"""
  }

  def americanise(correct: String) = correct match {
    case "Organisation" => "Organization"
    case _ => correct
  }

  def getEntities(term: String, key: String, types: Set[String]): Option[GoogleResponse] = {
    val request = new Request.Builder().url(buildUrl(term, key, types)).build
    val response = httpClient.newCall(request).execute
    if (response.isSuccessful) {
      val body = response.body.string
      decode[GoogleResponse](body).fold(
        { error =>
          println(s"Error parsing response for $term: $error")
          None
        },
        { data => Some(data) }
      )
    } else {
      println(s"Error requesting data for $term. Status code was: ${response.code}, body was: ${response.body.string}")
      None
    }
  }
}
