package com.gu.entity_indexer

import okhttp3.{OkHttpClient, Request}
import io.circe.parser.decode
import io.circe.generic.auto._

case class GoogleResponse(itemListElement: Seq[ItemListElement])
case class ItemListElement(result: GoogleEntity, resultScore: Double)
case class GoogleEntity(name: String, `@type`: Set[String], description: Option[String], `@id`: String)

object KnowledgeGraph {
  private val httpClient: OkHttpClient = new OkHttpClient()

  private def buildUrl(term: String, key: String): String = {
    s"""https://kgsearch.googleapis.com/v1/entities:search?query=${term.replaceAll(" ","+")}&key=$key&limit=5&types=Person&types=Place&types=Organization"""
  }

  def getEntities(term: String, key: String): Option[GoogleResponse] = {
    val request = new Request.Builder().url(buildUrl(term, key)).build
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
