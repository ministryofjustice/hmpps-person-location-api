package uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.Size
import org.hibernate.annotations.JdbcType
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.IdGenerator.newUuid
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.externalreference.ExternalReference
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.externalreference.ExternalReferenceConverter
import java.util.UUID

@Entity
@Table(name = "external_journey")
class ExternalJourney(
  @Size(max = 7)
  @Column(name = "person_identifier", nullable = false, length = 7)
  var personIdentifier: String,

  @JdbcType(PostgreSQLEnumJdbcType::class)
  @Column(name = "type", columnDefinition = "external_journey_type", nullable = false)
  var type: Type,

  @JdbcType(PostgreSQLEnumJdbcType::class)
  @Column(name = "status", columnDefinition = "external_journey_status", nullable = false)
  var status: Status,

  @Size(max = 6)
  @Column(name = "origin_prison_code", length = 6)
  var origin: String?,

  @Size(max = 6)
  @Column(name = "destination_prison_code", length = 6)
  var destination: String?,

  @Convert(converter = ExternalReferenceConverter::class)
  @Column(name = "external_reference")
  var externalReference: ExternalReference?,

  @Id
  @Column(name = "id", nullable = false)
  var id: UUID = newUuid(),
) {
  @Version
  @Column(name = "version", nullable = false)
  var version: Int? = null

  enum class Type { ADMISSION, COURT_APPEARANCE, TEMPORARY_ABSENCE, TRANSFER, RELEASE }
  enum class Status { SCHEDULED, IN_TRANSIT, COMPLETED, CANCELLED }
}
