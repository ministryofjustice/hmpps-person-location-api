package uk.gov.justice.digital.hmpps.personlocationapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.personlocationapi.event.domain.PrisonerMerged
import uk.gov.justice.digital.hmpps.personlocationapi.event.internal.InternalEventEmitter
import uk.gov.justice.digital.hmpps.personlocationapi.event.internal.RefreshPersonMovementIntervals
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.ExternalMovementRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeriesRepository

@Service
class PrisonerMergedHandler(
  private val transactionTemplate: TransactionTemplate,
  private val seriesRepository: CustodialSeriesRepository,
  private val movementRepository: ExternalMovementRepository,
  private val iee: InternalEventEmitter,
) {
  fun handle(de: PrisonerMerged) {
    val pmi = de.additionalInformation
    val updates = transactionTemplate.execute {
      movementRepository.findByPersonIdentifier(pmi.removedNomsNumber)
        .onEach { it.applyPerson(pmi.nomsNumber) }
      seriesRepository.findByPersonIdentifier(pmi.removedNomsNumber)
        .onEach { it.applyPerson(pmi.nomsNumber) }
        .takeIf { it.isNotEmpty() }
        ?.let { listOf(pmi.removedNomsNumber, pmi.nomsNumber).map { RefreshPersonMovementIntervals(it) } } ?: emptyList()
    }
    iee.publishInternalEvents(updates)
  }
}
