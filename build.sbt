lazy val scala212 = "2.12.13"
lazy val scala213 = "2.13.5"
lazy val supportedScalaVersions = List(scala212, scala213)


lazy val commonSettings = Seq(
  organization := "io.github.scala-bones",
  scalaVersion := "2.13.5",
  crossScalaVersions := supportedScalaVersions,
  version := "0.3.0",
  homepage := Some(url("https://github.com/scala-bones/scatonic-ideal")),
  startYear := Some(2020),
  licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT")),
  pomExtra := {
    <scm>
      <url>git://github.com/scala-bones/scatonic-deal.git</url>
      <connection>scm:git://github.com/scala-bones/scatonic-ideal.git</connection>
    </scm>
    <developers>
      <developer>
        <id>oletraveler</id>
        <name>Travis Stevens</name>
        <url>https://github.com/oletraveler</url>
      </developer>
    </developers>
  },
  resolvers += Resolver.sonatypeRepo("releases"),
  credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  publishMavenStyle := true,
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.3" cross CrossVersion.full),
)

lazy val core = (project in file("core"))
  .settings(
    commonSettings,
    name := "scatonic-ideal",
    libraryDependencies ++= Seq(
      "org.scalatestplus" %% "scalatestplus-scalacheck" % "3.1.0.0-RC2" % Test,
      "org.scalatest" %% "scalatest-mustmatchers" % "3.2.8" % Test
    ),
    description := "Core classes use for Native JDBC and Doobie Implementations"
  )

lazy val jdbc = (project in file("jdbc"))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    commonSettings,
    parallelExecution in IntegrationTest := false,
    name := "jdbc-scatonic-ideal",
    libraryDependencies ++= Seq(
      "org.postgresql" % "postgresql" % "42.2.23" % "test,it",
      "mysql" % "mysql-connector-java" % "8.0.24" % "test,it",
      "org.scalatest" %% "scalatest" % "3.2.8" % "test,it",
      "org.scalatestplus" %% "scalatestplus-scalacheck" % "3.1.0.0-RC2" % "test,it"
    )
  )
  .dependsOn(core)

/*lazy val doobieVersion = "0.9.0"
lazy val doobie = (project in file("doobie"))
  .settings(
    commonSettings,
    name := "doobie-scatonic-ideal",
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "doobie-core" % doobieVersion,      
      "org.scalatest" %% "scalatest" % "3.2.8" % Test,
      "org.scalatestplus" %% "scalatestplus-scalacheck" % "3.1.0.0-RC2" % Test
    )
  )
  .dependsOn(core)
*/


