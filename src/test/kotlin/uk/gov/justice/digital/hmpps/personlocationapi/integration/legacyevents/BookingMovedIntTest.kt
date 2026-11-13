package uk.gov.justice.digital.hmpps.personlocationapi.integration.legacyevents

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.personlocationapi.event.domain.BookingMoved
import uk.gov.justice.digital.hmpps.personlocationapi.event.domain.BookingMovedInformation
import uk.gov.justice.digital.hmpps.personlocationapi.event.domain.PersonReference
import uk.gov.justice.digital.hmpps.personlocationapi.event.internal.RefreshPersonMovementIntervals
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.newId
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.personlocationapi.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialSeriesOperations
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialSeriesOperationsImpl.Companion.custodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperations
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperationsImpl.Companion.externalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisode
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisodeRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalJourney
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.service.RefreshExternalMovementIntervals
import java.time.LocalDateTime

class BookingMovedIntTest(
  @Autowired seriesOps: CustodialSeriesOperations,
  @Autowired moveOps: ExternalMovementOperations,
  @Autowired private val refresh: RefreshExternalMovementIntervals,
  @Autowired private val episodeRepository: CustodialEpisodeRepository,
) : IntegrationTest(),
  ExternalMovementOperations by moveOps,
  CustodialSeriesOperations by seriesOps {

  @Test
  fun `moves custody series to new identifier`() {
    val fromIdentifier = personIdentifier()
    val toIdentifier = personIdentifier()

    val fromSeries = givenSeries(custodialSeries(fromIdentifier, legacyId = newId()))
    val fromMovements = listOf(
      givenMovement(
        externalMovement(
          fromSeries,
          movementType = ExternalMovement.Type.ARRIVAL,
          journeyType = ExternalJourney.Type.ADMISSION,
          occurredAt = LocalDateTime.now().minusWeeks(13),
        ),
      ),
      givenMovement(
        externalMovement(
          fromSeries,
          movementType = ExternalMovement.Type.DEPARTURE,
          journeyType = ExternalJourney.Type.RELEASE,
          occurredAt = LocalDateTime.now().minusWeeks(1),
        ),
      ),
    )
    val seriesToMove = givenSeries(custodialSeries(fromIdentifier, legacyId = newId()))
    val movementToMove = givenMovement(
      externalMovement(
        seriesToMove,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        occurredAt = LocalDateTime.now().minusDays(1),
      ),
    )
    val toSeries = givenSeries(custodialSeries(toIdentifier, legacyId = newId()))
    val toMovements = listOf(
      givenMovement(
        externalMovement(
          toSeries,
          movementType = ExternalMovement.Type.ARRIVAL,
          journeyType = ExternalJourney.Type.ADMISSION,
          occurredAt = LocalDateTime.now().minusWeeks(13),
        ),
      ),
      givenMovement(
        externalMovement(
          toSeries,
          movementType = ExternalMovement.Type.DEPARTURE,
          journeyType = ExternalJourney.Type.RELEASE,
          occurredAt = LocalDateTime.now().minusWeeks(1),
        ),
      ),
    )
    refresh.execute(RefreshPersonMovementIntervals(fromIdentifier))
    assertThat(episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(fromIdentifier)).hasSize(2)
    refresh.execute(RefreshPersonMovementIntervals(toIdentifier))
    assertThat(episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(toIdentifier)).hasSize(1)

    sendDomainEvent(
      bookingMovedEvent(
        seriesToMove.legacyId!!,
        fromIdentifier,
        toIdentifier,
        seriesToMove.openedAt,
        seriesToMove.closedAt,
      ),
    )

    waitUntil { findSeries(seriesToMove.id)!!.version!! > 0 }

    val moved = requireNotNull(findSeries(seriesToMove.id))
    assertThat(moved.personIdentifier).isEqualTo(toIdentifier)
    with(movementToMove.id) {
      val found = findMovement(this)
      assertThat(found?.personIdentifier).isEqualTo(toIdentifier)
    }
    val fs = requireNotNull(findSeries(fromSeries.id))
    assertThat(fs.personIdentifier).isEqualTo(fromIdentifier)
    fromMovements.forEach {
      val found = findMovement(it.id)
      assertThat(found?.personIdentifier).isEqualTo(fromIdentifier)
    }
    val ts = requireNotNull(findSeries(toSeries.id))
    assertThat(ts.personIdentifier).isEqualTo(toIdentifier)
    toMovements.forEach {
      val found = findMovement(it.id)
      assertThat(found?.personIdentifier).isEqualTo(toIdentifier)
    }

    waitUntil { episodeRepository.findMostRecentFor(toIdentifier).isNotEmpty() }
    val to = episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(toIdentifier)
    assertThat(to).hasSize(2)
    assertThat(to.first().commencedAt).isEqualTo(movementToMove.occurredAt)

    waitUntil { episodeRepository.findMostRecentFor(fromIdentifier).firstOrNull()?.status == CustodialEpisode.Status.COMPLETED }
    val from = episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(fromIdentifier)
    assertThat(from).hasSize(1)
  }

  companion object {
    fun bookingMovedEvent(bookingId: Long, from: String, to: String, start: LocalDateTime, end: LocalDateTime?) = BookingMoved(
      BookingMovedInformation(bookingId, from, to, start, end),
      PersonReference.withIdentifier(to),
    )
  }
}
