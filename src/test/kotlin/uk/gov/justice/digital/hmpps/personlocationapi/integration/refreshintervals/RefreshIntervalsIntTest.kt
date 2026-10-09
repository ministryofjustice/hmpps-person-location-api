package uk.gov.justice.digital.hmpps.personlocationapi.integration.refreshintervals

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.personlocationapi.RefreshExternalMovementIntervals
import uk.gov.justice.digital.hmpps.personlocationapi.event.internal.RefreshPersonMovementIntervals
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.externalReference
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.personlocationapi.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialSeriesOperations
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialSeriesOperationsImpl.Companion.custodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperations
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperationsImpl.Companion.externalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperationsImpl.Companion.prison
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisode
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalJourney
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.PrisonStay
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.PrisonStayRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.externalreference.ExternalReferenceEntity
import java.time.LocalDate

class RefreshIntervalsIntTest(
  @Autowired seriesOps: CustodialSeriesOperations,
  @Autowired moveOps: ExternalMovementOperations,
  @Autowired private val refresh: RefreshExternalMovementIntervals,
  @Autowired private val stayRepository: PrisonStayRepository,
) : IntegrationTest(),
  CustodialSeriesOperations by seriesOps,
  ExternalMovementOperations by moveOps {

  @Test
  fun `can correctly add new stays and episodes for single series`() {
    val personIdentifier = personIdentifier()
    val start = LocalDate.now().atTime(10, 0).minusDays(14)
    val series = givenSeries(custodialSeries(personIdentifier, openedAt = start))
    val prisonOne = prison()
    val prisonTwo = prison()
    val admission = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        destination = prisonOne,
        occurredAt = start,
      ),
    )
    val tapDep = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.TEMPORARY_ABSENCE,
        origin = prisonOne,
        scheduleReference = externalReference(ExternalReferenceEntity.TEMPORARY_ABSENCE),
        occurredAt = admission.occurredAt.plusDays(1),
      ),
    )
    val tapArr = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.TEMPORARY_ABSENCE,
        destination = prisonOne,
        occurredAt = tapDep.occurredAt.plusHours(4),
      ),
    )
    val trDep = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.TRANSFER,
        origin = prisonOne,
        destination = prisonTwo,
        occurredAt = tapArr.occurredAt.plusDays(2),
      ),
    )
    val trArr = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        origin = prisonOne,
        destination = prisonTwo,
        occurredAt = trDep.occurredAt.plusHours(2),
      ),
    )
    val courtApp = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.COURT_APPEARANCE,
        origin = prisonTwo,
        scheduleReference = externalReference(ExternalReferenceEntity.COURT_APPEARANCE),
        occurredAt = trArr.occurredAt.plusDays(1),
      ),
    )
    val rel = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        origin = prisonTwo,
        occurredAt = courtApp.occurredAt.plusHours(4),
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))

    val stays = transactionTemplate.execute {
      stayRepository.findAllByPersonIdentifier(personIdentifier).sortedBy { it.arrivedAt }.onEach { it.movements() }
    }
    assertThat(stays).hasSize(2)
    with(stays.first()) {
      assertThat(arrivedAt).isEqualTo(admission.occurredAt)
      assertThat(departedAt).isEqualTo(trArr.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.TRANSFERRED_OUT)
      assertThat(movements().map { it.id }).containsExactly(admission.id, tapDep.id, tapArr.id, trDep.id)
    }
    with(stays.last()) {
      assertThat(arrivedAt).isEqualTo(trArr.occurredAt)
      assertThat(departedAt).isEqualTo(rel.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
      assertThat(movements().map { it.id }).containsExactly(trArr.id, courtApp.id, rel.id)
    }
    assertThat(stays.first().episode.id).isEqualTo(stays.last().episode.id)
    val episode = stays.first().episode
    assertThat(episode.commencedAt).isEqualTo(stays.first().arrivedAt)
    assertThat(episode.concludedAt).isEqualTo(stays.last().departedAt)
    assertThat(episode.status).isEqualTo(CustodialEpisode.Status.COMPLETED)
  }

  @Test
  fun `can correctly add new stays and episodes for multiple series`() {
    val personIdentifier = personIdentifier()
    val start = LocalDate.now().atTime(10, 0).minusDays(14)
    val prisonOne = prison()
    val prisonTwo = prison()
    val seriesOne = givenSeries(
      custodialSeries(
        personIdentifier,
        openedAt = start,
        closedAt = start.plusDays(5),
        status = CustodialSeries.Status.CLOSED,
        isActive = false,
      ),
    )
    val admissionOne = givenMovement(
      externalMovement(
        seriesOne,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        destination = prisonOne,
        occurredAt = start,
      ),
    )
    val relOne = givenMovement(
      externalMovement(
        seriesOne,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        origin = prisonOne,
        occurredAt = admissionOne.occurredAt.plusDays(2),
      ),
    )
    val seriesTwo = givenSeries(
      custodialSeries(
        personIdentifier,
        openedAt = start.plusDays(7),
      ),
    )
    val admissionTwo = givenMovement(
      externalMovement(
        seriesTwo,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        destination = prisonTwo,
        occurredAt = seriesTwo.openedAt,
      ),
    )
    val tapDep = givenMovement(
      externalMovement(
        seriesTwo,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.TEMPORARY_ABSENCE,
        origin = prisonTwo,
        scheduleReference = externalReference(ExternalReferenceEntity.TEMPORARY_ABSENCE),
        occurredAt = admissionTwo.occurredAt.plusDays(1),
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))

    val stays = transactionTemplate.execute {
      stayRepository.findAllByPersonIdentifier(personIdentifier).sortedBy { it.arrivedAt }.onEach { it.movements() }
    }
    assertThat(stays).hasSize(2)
    with(stays.first()) {
      assertThat(arrivedAt).isEqualTo(admissionOne.occurredAt)
      assertThat(departedAt).isEqualTo(relOne.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
      assertThat(movements().map { it.id }).containsExactly(admissionOne.id, relOne.id)
    }
    with(stays.last()) {
      assertThat(arrivedAt).isEqualTo(admissionTwo.occurredAt)
      assertThat(departedAt).isNull()
      assertThat(status).isEqualTo(PrisonStay.Status.IN_TRANSIT_EXTERNAL)
      assertThat(movements().map { it.id }).containsExactly(admissionTwo.id, tapDep.id)
    }
    assertThat(stays.first().episode.id).isNotEqualTo(stays.last().episode.id)
    val episodeOne = stays.first().episode
    assertThat(episodeOne.commencedAt).isEqualTo(stays.first().arrivedAt)
    assertThat(episodeOne.concludedAt).isEqualTo(stays.first().departedAt)
    assertThat(episodeOne.status).isEqualTo(CustodialEpisode.Status.COMPLETED)

    val episodeTwo = stays.last().episode
    assertThat(episodeTwo.commencedAt).isEqualTo(stays.last().arrivedAt)
    assertThat(episodeTwo.concludedAt).isNull()
    assertThat(episodeTwo.status).isEqualTo(CustodialEpisode.Status.ACTIVE)
  }
}
