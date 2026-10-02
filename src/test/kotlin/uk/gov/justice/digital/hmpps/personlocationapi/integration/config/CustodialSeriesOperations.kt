package uk.gov.justice.digital.hmpps.personlocationapi.integration.config

import org.springframework.data.repository.findByIdOrNull
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.word
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.IdGenerator.newUuid
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeries.Status
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeriesRepository
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

typealias CustodialSeriesProvider = () -> CustodialSeries

interface CustodialSeriesOperations {
  fun findSeries(uuid: UUID): CustodialSeries?
  fun givenSeries(sp: CustodialSeriesProvider): CustodialSeries
}

class CustodialSeriesOperationsImpl(
  private val seriesRepository: CustodialSeriesRepository,
) : CustodialSeriesOperations {
  override fun findSeries(uuid: UUID): CustodialSeries? = seriesRepository.findByIdOrNull(uuid)
  override fun givenSeries(sp: CustodialSeriesProvider): CustodialSeries = seriesRepository.save(sp())

  companion object {
    fun custodialSeries(
      personIdentifier: String = personIdentifier(),
      status: Status = Status.OPEN,
      isActive: Boolean = true,
      openedAt: LocalDateTime = LocalDateTime.now().minusDays(1),
      closedAt: LocalDateTime? = null,
      notes: String? = word(10),
      legacyBookingReference: String? = null,
      legacyId: Long? = null,
      id: UUID = newUuid(),
    ): CustodialSeriesProvider = {
      CustodialSeries(
        personIdentifier,
        status,
        isActive,
        openedAt.truncatedTo(ChronoUnit.SECONDS),
        closedAt,
        notes,
        legacyBookingReference,
        legacyId,
        id,
      )
    }
  }
}
