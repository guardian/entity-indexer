package com.gu.entity_indexer

import java.io.File

import com.amazonaws.auth.AWSCredentialsProviderChain
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.kinesis.AmazonKinesisClient
import com.gu.contentapi.client.GuardianContentClient
import com.typesafe.config.ConfigFactory

import scala.util.Try

case class KinesisConfig(streamName: String, stsRoleArn: String, kinesisClient: AmazonKinesisClient)
case class Config(kinesis: KinesisConfig, capi: GuardianContentClient, googleKey: String)

object Config {
  private val userHome = System.getProperty("user.home")
  private val rootConfig = Try(ConfigFactory.parseFile(new File(s"$userHome/.gu/entity-indexer.conf"))).toOption getOrElse sys.error("Could not find config file. This application will not run.")

  def apply(stage: String): Config = {
    val conf = Try(rootConfig.getConfig(s"entity-indexer.${stage.toLowerCase}")).toOption getOrElse sys.error("Could not retrieve stage sensitive config. This application will not run.")

    def getMandatoryString(item: String) = Try(conf.getString(item)).toOption getOrElse sys.error(s"Could not get item $item from config. Exiting.")

    val streamName = getMandatoryString("streamName")
    val stsRoleArn = getMandatoryString("stsRoleArn")

    val kinesisConfig = KinesisConfig(
      streamName,
      stsRoleArn,
      kinesisClient = {
        val kinesisCredentialsProvider = new AWSCredentialsProviderChain(new ProfileCredentialsProvider("capi"))

        val kinesisClient = new AmazonKinesisClient(kinesisCredentialsProvider)
        kinesisClient.setRegion(Region getRegion Regions.fromName("eu-west-1"))
        kinesisClient
      }
    )

    val capiApiKey = getMandatoryString("capiKey")

    val googleKey = getMandatoryString("googleKey")

    Config(kinesisConfig, new GuardianContentClient(capiApiKey), googleKey)
  }
}
