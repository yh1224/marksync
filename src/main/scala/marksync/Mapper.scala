package marksync

import java.io.File

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule

object Mapper {
  val jsonMapper = new ObjectMapper()
  jsonMapper.registerModule(DefaultScalaModule)
  jsonMapper.getTypeFactory.constructMapLikeType(classOf[Map[String, String]], classOf[String], classOf[String])
  jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  val yamlMapper = new ObjectMapper(new YAMLFactory())
  yamlMapper.registerModule(DefaultScalaModule)
  jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  def readJson[A](str: String, aClass: Class[A]): A = jsonMapper.readValue(str, aClass)

  def readYaml[A](file: File, aClass: Class[A]): A = yamlMapper.readValue(file, aClass)

  def writeYaml[A](file: File, obj: A): Unit = yamlMapper.writeValue(file, obj)

  def getJson[A](obj: A): String = jsonMapper.writeValueAsString(obj)
}
