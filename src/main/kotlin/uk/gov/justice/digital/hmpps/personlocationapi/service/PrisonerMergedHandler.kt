package uk.gov.justice.digital.hmpps.personlocationapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.personlocationapi.event.domain.PrisonerMerged
import uk.gov.justice.digital.hmpps.personlocationapi.integration.nomis.MigrationClient
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.ExternalMovementRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeriesRepository

@Service
class PrisonerMergedHandler(
  private val transactionTemplate: TransactionTemplate,
  private val seriesRepository: CustodialSeriesRepository,
  private val movementRepository: ExternalMovementRepository,
  private val migrationClient: MigrationClient,
) {
  fun handle(de: PrisonerMerged) {
    val pmi = de.additionalInformation
    transactionTemplate.execute {
      val series = seriesRepository.findByPersonIdentifier(pmi.removedNomsNumber)
        .onEach { it.applyPerson(pmi.nomsNumber) }
      val movements = movementRepository.findByPersonIdentifier(pmi.removedNomsNumber)
        .onEach { it.applyPerson(pmi.nomsNumber) }
      return@execute if (series.isNotEmpty() || movements.isNotEmpty()) {
        listOf(pmi.nomsNumber, pmi.removedNomsNumber)
      } else {
        null
      }
    }?.forEach { pi ->
      /* repair will also refresh episodes/stays */
      migrationClient.requestRepair(pi)
    }
  }
}
