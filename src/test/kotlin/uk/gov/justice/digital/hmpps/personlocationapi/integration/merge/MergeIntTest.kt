package uk.gov.justice.digital.hmpps.personlocationapi.integration.merge

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.personlocationapi.event.domain.PersonReference
import uk.gov.justice.digital.hmpps.personlocationapi.event.domain.PrisonerMerged
import uk.gov.justice.digital.hmpps.personlocationapi.event.domain.PrisonerMergedInformation
import uk.gov.justice.digital.hmpps.personlocationapi.event.internal.RefreshPersonMovementIntervals
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.personlocationapi.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialSeriesOperations
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialSeriesOperationsImpl.Companion.custodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperations
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperationsImpl.Companion.externalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisodeRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalJourney
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.service.RefreshExternalMovementIntervals

class MergeIntTest(
  @Autowired seriesOps: CustodialSeriesOperations,
  @Autowired moveOps: ExternalMovementOperations,
  @Autowired private val refresh: RefreshExternalMovementIntervals,
  @Autowired private val episodeRepository: CustodialEpisodeRepository,
) : IntegrationTest(),
  ExternalMovementOperations by moveOps,
  CustodialSeriesOperations by seriesOps {
  @Test
  fun `non consistent merge events are ignored`() {
    val series = givenSeries(custodialSeries())
    givenMovement(externalMovement(series))

    val toIdentifier = personIdentifier()
    sendDomainEvent(mergeEvent(personIdentifier(), toIdentifier))

    val saved = requireNotNull(findSeries(series.id))
    assertThat(saved.personIdentifier).isEqualTo(series.personIdentifier)
  }

  @Test
  fun `merge events move series to new identifier`() {
    val fromIdentifier = personIdentifier()
    val toIdentifier = personIdentifier()

    val series = givenSeries(custodialSeries(fromIdentifier))
    val movement = givenMovement(externalMovement(series, movementType = ExternalMovement.Type.ARRIVAL, journeyType = ExternalJourney.Type.ADMISSION))
    refresh.execute(RefreshPersonMovementIntervals(fromIdentifier))

    sendDomainEvent(mergeEvent(fromIdentifier, toIdentifier))

    waitUntil { findSeries(series.id)!!.version!! > 0 }

    val saved = requireNotNull(findSeries(series.id))
    assertThat(saved.personIdentifier).isEqualTo(toIdentifier)

    waitUntil { episodeRepository.findMostRecentFor(toIdentifier).isNotEmpty() }

    val episodes = episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(toIdentifier)
    assertThat(episodes.size).isEqualTo(1)
    assertThat(episodes.first().commencedAt).isEqualTo(movement.occurredAt)

    val removed = episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(fromIdentifier)
    assertThat(removed).isEmpty()
  }

  companion object {
    fun mergeEvent(from: String, to: String) = PrisonerMerged(
      PrisonerMergedInformation(from, to),
      PersonReference.withIdentifier(to),
    )
  }
}
