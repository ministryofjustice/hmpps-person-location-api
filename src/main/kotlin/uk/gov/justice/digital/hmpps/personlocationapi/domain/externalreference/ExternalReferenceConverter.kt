package uk.gov.justice.digital.hmpps.personlocationapi.domain.externalreference

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class ExternalReferenceConverter : AttributeConverter<ExternalReference, String> {
  override fun convertToDatabaseColumn(attribute: ExternalReference?): String? = attribute?.toString()
  override fun convertToEntityAttribute(dbData: String?): ExternalReference? = dbData?.let { ExternalReference.fromString(it) }
}
