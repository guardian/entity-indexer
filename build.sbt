name := "entity-indexer"

version := "1.0"

scalaVersion := "2.11.8"

val CirceVersion = "0.7.0"
val AwsSdkVersion = "1.11.46"

scroogeThriftOutputFolder in Compile := sourceManaged.value / "thrift"
scroogeThriftSourceFolder in Compile := baseDirectory.value / "src/main/thrift"

scroogeThriftDependencies in Compile ++= Seq(
  "content-entity-thrift"
)

scroogeThriftSources in Compile ++= {
  (scroogeUnpackDeps in Compile).value.flatMap { dir => (dir ** "*.thrift").get }
}

includeFilter in unmanagedResources := "*.thrift"

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.3.1",
  "org.apache.thrift" % "libthrift" % "0.9.1",
  "com.twitter" %% "scrooge-core" % "4.5.0",
  "com.gu" % "content-entity-thrift" % "0.1.5",
  "com.gu" %% "content-api-client" % "11.9",
  "com.squareup.okhttp3" % "okhttp" % "3.4.2",
  "io.circe" %% "circe-core" % CirceVersion,
  "io.circe" %% "circe-generic" % CirceVersion,
  "io.circe" %% "circe-parser" % CirceVersion,
  "com.amazonaws" % "aws-java-sdk-sts" % AwsSdkVersion,
  "com.amazonaws" % "aws-java-sdk-kinesis" % AwsSdkVersion
)
