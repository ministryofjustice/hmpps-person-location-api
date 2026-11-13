package uk.gov.justice.digital.hmpps.personlocationapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.personlocationapi.event.domain.BookingMoved
import uk.gov.justice.digital.hmpps.personlocationapi.event.internal.InternalEventEmitter
import uk.gov.justice.digital.hmpps.personlocationapi.event.internal.RefreshPersonMovementIntervals
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.ExternalMovementRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeriesRepository

@Service
class BookingMovedHandler(
  private val transactionTemplate: TransactionTemplate,
  private val seriesRepository: CustodialSeriesRepository,
  private val movementRepository: ExternalMovementRepository,
  private val iee: InternalEventEmitter,
) {
  fun handle(de: BookingMoved) {
    val bmi = de.additionalInformation
    val updates = transactionTemplate.execute {
      val series = requireNotNull(seriesRepository.findByLegacyId(bmi.bookingId))
      return@execute if (series.personIdentifier == bmi.movedFromNomsNumber) {
        series.applyPerson(bmi.movedToNomsNumber)
        movementRepository.findBySeriesId(series.id).forEach { it.applyPerson(bmi.movedToNomsNumber) }
        listOf(bmi.movedFromNomsNumber, bmi.movedToNomsNumber).map { RefreshPersonMovementIntervals(it) }
      } else {
        emptyList()
      }
    }
    iee.publishInternalEvents(updates)
  }
}
