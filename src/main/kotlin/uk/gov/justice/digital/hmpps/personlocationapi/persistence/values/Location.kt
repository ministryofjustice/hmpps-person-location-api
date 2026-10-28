package uk.gov.justice.digital.hmpps.personlocationapi.persistence.values

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping
import io.swagger.v3.oas.annotations.media.Schema

const val LOCATION_TYPE = "locationType"
const val PRISON = "Prison"
const val COURT = "Court"
const val SECURE_HOSPITAL = "SecureHospital"
const val SIMPLE_LOCATION = "SimpleLocation"

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = LOCATION_TYPE)
@JsonSubTypes(
  JsonSubTypes.Type(value = Prison::class, name = PRISON),
  JsonSubTypes.Type(value = Court::class, name = COURT),
  JsonSubTypes.Type(value = SecureHospital::class, name = SECURE_HOSPITAL),
  JsonSubTypes.Type(value = SimpleLocation::class, name = SIMPLE_LOCATION),
)
@Schema(
  description = "Represents a movement origin or destination location",
  discriminatorProperty = LOCATION_TYPE,
  discriminatorMapping = [
    DiscriminatorMapping(value = PRISON, schema = Prison::class),
    DiscriminatorMapping(value = COURT, schema = Court::class),
    DiscriminatorMapping(value = SECURE_HOSPITAL, schema = SecureHospital::class),
    DiscriminatorMapping(value = SIMPLE_LOCATION, schema = SimpleLocation::class),
  ],
  subTypes = [Prison::class, Court::class, SecureHospital::class, SimpleLocation::class],
)
sealed interface Location {
  val description: String
}

@Schema(description = "Prison location", name = PRISON)
data class Prison(val code: String, override val description: String) : Location

@Schema(description = "Court location", name = COURT)
data class Court(val code: String, override val description: String) : Location

@Schema(description = "Hospital location", name = SECURE_HOSPITAL)
data class SecureHospital(val code: String, override val description: String) : Location

@Schema(description = "Generic location", name = SIMPLE_LOCATION)
data class SimpleLocation(override val description: String) : Location
