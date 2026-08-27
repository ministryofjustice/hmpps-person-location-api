package uk.gov.justice.digital.hmpps.personlocationapi.config

import com.fasterxml.jackson.core.JsonProcessingException
import org.hibernate.cfg.AvailableSettings
import org.hibernate.type.descriptor.WrapperOptions
import org.hibernate.type.descriptor.java.JavaType
import org.hibernate.type.format.AbstractJsonFormatMapper
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Configuration
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.lang.reflect.Type

@Configuration
class HibernateCustomizer(
  private val jsonMapper: JsonMapper,
) : HibernatePropertiesCustomizer {
  override fun customize(hibernateProperties: MutableMap<String, Any>) {
    hibernateProperties[AvailableSettings.JSON_FORMAT_MAPPER] = JacksonJson3FormatMapper(jsonMapper)
  }
}

class JacksonJson3FormatMapper(private val jsonMapper: JsonMapper) : AbstractJsonFormatMapper() {

  @Throws(IOException::class)
  override fun <T> writeToTarget(value: T?, javaType: JavaType<T?>, target: Any?, options: WrapperOptions?) {
    jsonMapper.writerFor(jsonMapper.constructType(javaType.javaType))
      .writeValue(target as JsonGenerator?, value)
  }

  @Throws(IOException::class)
  override fun <T> readFromSource(javaType: JavaType<T?>, source: Any?, options: WrapperOptions?): T? = jsonMapper.readValue(source as JsonParser?, jsonMapper.constructType(javaType.javaType))

  override fun supportsSourceType(sourceType: Class<*>): Boolean = JsonParser::class.java.isAssignableFrom(sourceType)

  override fun supportsTargetType(targetType: Class<*>): Boolean = JsonGenerator::class.java.isAssignableFrom(targetType)

  override fun <T> fromString(charSequence: CharSequence, type: Type?): T? {
    try {
      return jsonMapper.readValue(charSequence.toString(), jsonMapper.constructType(type))
    } catch (e: JsonProcessingException) {
      throw IllegalArgumentException("Could not deserialize string to type: $type", e)
    }
  }

  public override fun <T> toString(value: T?, type: Type?): String? {
    try {
      return jsonMapper.writerFor(jsonMapper.constructType(type)).writeValueAsString(value)
    } catch (e: JsonProcessingException) {
      throw IllegalArgumentException("Could not serialize object of type: $type", e)
    }
  }
}
