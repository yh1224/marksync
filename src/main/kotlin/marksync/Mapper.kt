package marksync

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

object Mapper {
    private val jsonMapper = ObjectMapper()
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)

    private val yamlMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)

    fun <A> readJson(str: String, aClass: Class<A>): A = jsonMapper.readValue(str, aClass)

    fun <A> readYaml(str: String, aClass: Class<A>): A = yamlMapper.readValue(str, aClass)

    fun <A> readYaml(file: File, aClass: Class<A>): A = yamlMapper.readValue(file, aClass)

    fun <A> writeYaml(file: File, obj: A): Unit = yamlMapper.writeValue(file, obj)

    fun <A> getJson(obj: A): String = jsonMapper.writeValueAsString(obj)
}
