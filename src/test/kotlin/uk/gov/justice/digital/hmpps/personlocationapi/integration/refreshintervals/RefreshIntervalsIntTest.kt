package uk.gov.justice.digital.hmpps.personlocationapi.integration.refreshintervals

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.personlocationapi.event.internal.RefreshPersonMovementIntervals
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.externalReference
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.newId
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.personlocationapi.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialSeriesOperations
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialSeriesOperationsImpl.Companion.custodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperations
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperationsImpl.Companion.externalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperationsImpl.Companion.prison
import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.ApplyOccurredAt
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisode
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisodeRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalJourney
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalJourneyRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.PrisonStay
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.PrisonStayRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.CodedReason
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.SimpleLocation
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.externalreference.ExternalReferenceEntity
import java.time.LocalDate
import java.time.LocalDateTime

class RefreshIntervalsIntTest(
  @Autowired seriesOps: CustodialSeriesOperations,
  @Autowired moveOps: ExternalMovementOperations,
  @Autowired private val stayRepository: PrisonStayRepository,
  @Autowired private val episodeRepository: CustodialEpisodeRepository,
  @Autowired private val journeyRepository: ExternalJourneyRepository,
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

    val stays = stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)
    assertThat(stays).hasSize(2)
    with(stays.first()) {
      assertThat(arrivedAt).isEqualTo(admission.occurredAt)
      assertThat(departedAt).isEqualTo(trDep.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.TRANSFERRED_OUT)
    }
    with(stays.last()) {
      assertThat(arrivedAt).isEqualTo(trArr.occurredAt)
      assertThat(departedAt).isEqualTo(rel.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
    }

    val episode = episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(personIdentifier).single()
    assertThat(episode.commencedAt).isEqualTo(stays.first().arrivedAt)
    assertThat(episode.concludedAt).isEqualTo(stays.last().departedAt)
    assertThat(episode.status).isEqualTo(CustodialEpisode.Status.COMPLETED)

    val journeys = journeyRepository.findAllByPersonIdentifierOrderByCommencedAtAsc(personIdentifier)
    assertThat(journeys).hasSize(3)
    with(journeys.first()) {
      assertThat(type).isEqualTo(ExternalJourney.Type.TEMPORARY_ABSENCE)
      assertThat(externalReference).isEqualTo(tapDep.scheduleReference!!)
      assertThat(origin).isEqualTo(prisonOne.code)
      assertThat(destination).isEqualTo(prisonOne.code)
    }
    with(journeys[1]) {
      assertThat(type).isEqualTo(ExternalJourney.Type.TRANSFER)
      assertThat(origin).isEqualTo(prisonOne.code)
      assertThat(destination).isEqualTo(prisonTwo.code)
    }
    with(journeys.last()) {
      assertThat(type).isEqualTo(ExternalJourney.Type.COURT_APPEARANCE)
      assertThat(externalReference).isEqualTo(courtApp.scheduleReference!!)
      assertThat(origin).isEqualTo(prisonTwo.code)
    }
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

    val stays = stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)
    assertThat(stays).hasSize(2)
    with(stays.first()) {
      assertThat(arrivedAt).isEqualTo(admissionOne.occurredAt)
      assertThat(departedAt).isEqualTo(relOne.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
    }
    with(stays.last()) {
      assertThat(arrivedAt).isEqualTo(admissionTwo.occurredAt)
      assertThat(departedAt).isNull()
      assertThat(status).isEqualTo(PrisonStay.Status.IN_TRANSIT_EXTERNAL)
    }
    val episodes = episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(personIdentifier).reversed()
    assertThat(episodes).hasSize(2)

    val episodeOne = episodes.first()
    assertThat(episodeOne.commencedAt).isEqualTo(stays.first().arrivedAt)
    assertThat(episodeOne.concludedAt).isEqualTo(stays.first().departedAt)
    assertThat(episodeOne.status).isEqualTo(CustodialEpisode.Status.COMPLETED)

    val episodeTwo = episodes.last()
    assertThat(episodeTwo.commencedAt).isEqualTo(stays.last().arrivedAt)
    assertThat(episodeTwo.concludedAt).isNull()
    assertThat(episodeTwo.status).isEqualTo(CustodialEpisode.Status.TEMPORARY_ABSENCE)

    val journeys = journeyRepository.findAllByPersonIdentifierOrderByCommencedAtAsc(personIdentifier)
    assertThat(journeys).hasSize(1)
    with(journeys.single()) {
      assertThat(type).isEqualTo(ExternalJourney.Type.TEMPORARY_ABSENCE)
      assertThat(externalReference).isEqualTo(tapDep.scheduleReference!!)
      assertThat(status).isEqualTo(ExternalJourney.Status.IN_TRANSIT)
      assertThat(origin).isEqualTo(prisonTwo.code)
      assertThat(destination).isNull()
    }
  }

  @Test
  fun `editing tap movement does not change stay dates`() {
    val personIdentifier = personIdentifier()
    val start = LocalDate.now().atTime(10, 0).minusDays(14)
    val series = givenSeries(custodialSeries(personIdentifier, openedAt = start))
    val prisonOne = prison()
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
    val rel = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        origin = prisonOne,
        occurredAt = tapArr.occurredAt.plusDays(5),
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))

    val validateStays = {
      val stay = stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier).single()
      assertThat(stay.arrivedAt).isEqualTo(admission.occurredAt)
      assertThat(stay.departedAt).isEqualTo(rel.occurredAt)
      assertThat(stay.status).isEqualTo(PrisonStay.Status.RELEASED)
      val episode = episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(personIdentifier).single()
      assertThat(episode.commencedAt).isEqualTo(stay.arrivedAt)
      assertThat(episode.concludedAt).isEqualTo(stay.departedAt)
      assertThat(episode.status).isEqualTo(CustodialEpisode.Status.COMPLETED)
    }

    validateStays()

    transactionTemplate.executeWithoutResult {
      val dep = requireNotNull(findMovement(tapDep.id))
        .applyOccurredAt(ApplyOccurredAt(tapDep.occurredAt.plusDays(1)))
      requireNotNull(findMovement(tapArr.id))
        .applyOccurredAt(ApplyOccurredAt(dep.occurredAt.plusHours(6)))
    }

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier, tapDep.occurredAt))

    validateStays()

    val journeys = journeyRepository.findAllByPersonIdentifierOrderByCommencedAtAsc(personIdentifier)
    assertThat(journeys).hasSize(1)
    with(journeys.single()) {
      assertThat(type).isEqualTo(ExternalJourney.Type.TEMPORARY_ABSENCE)
      assertThat(externalReference).isEqualTo(tapDep.scheduleReference!!)
      assertThat(origin).isEqualTo(prisonOne.code)
      assertThat(destination).isEqualTo(prisonOne.code)
    }
  }

  @Test
  fun `can correctly update stay and episode when movements updated`() {
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
    val relTwo = givenMovement(
      externalMovement(
        seriesTwo,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        origin = prisonTwo,
        occurredAt = admissionTwo.occurredAt.plusDays(5),
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))

    val stays = stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)
    assertThat(stays).hasSize(2)
    with(stays.first()) {
      assertThat(arrivedAt).isEqualTo(admissionOne.occurredAt)
      assertThat(departedAt).isEqualTo(relOne.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
    }
    with(stays.last()) {
      assertThat(arrivedAt).isEqualTo(admissionTwo.occurredAt)
      assertThat(departedAt).isEqualTo(relTwo.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
    }

    val episodes = episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(personIdentifier).reversed()
    assertThat(episodes).hasSize(2)

    val episodeOne = episodes.first()
    assertThat(episodeOne.commencedAt).isEqualTo(stays.first().arrivedAt)
    assertThat(episodeOne.concludedAt).isEqualTo(stays.first().departedAt)
    assertThat(episodeOne.status).isEqualTo(CustodialEpisode.Status.COMPLETED)

    val episodeTwo = episodes.last()
    assertThat(episodeTwo.commencedAt).isEqualTo(stays.last().arrivedAt)
    assertThat(episodeTwo.concludedAt).isEqualTo(stays.last().departedAt)
    assertThat(episodeTwo.status).isEqualTo(CustodialEpisode.Status.COMPLETED)

    transactionTemplate.executeWithoutResult {
      requireNotNull(findMovement(admissionTwo.id))
        .applyOccurredAt(ApplyOccurredAt(admissionTwo.occurredAt.plusDays(1)))
      requireNotNull(findMovement(relTwo.id))
        .applyOccurredAt(ApplyOccurredAt(relTwo.occurredAt.minusDays(1)))
    }

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier, admissionTwo.occurredAt))

    val updatedStays = stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)

    assertThat(updatedStays).hasSize(2)
    with(updatedStays.first()) {
      assertThat(arrivedAt).isEqualTo(admissionOne.occurredAt)
      assertThat(departedAt).isEqualTo(relOne.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
    }
    with(updatedStays.last()) {
      assertThat(arrivedAt).isEqualTo(admissionTwo.occurredAt.plusDays(1))
      assertThat(departedAt).isEqualTo(relTwo.occurredAt.minusDays(1))
      assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
    }
    val ep = episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(personIdentifier).reversed()
    assertThat(episodes).hasSize(2)

    val ep1 = ep.first()
    assertThat(ep1.commencedAt).isEqualTo(updatedStays.first().arrivedAt)
    assertThat(ep1.concludedAt).isEqualTo(updatedStays.first().departedAt)
    assertThat(ep1.status).isEqualTo(CustodialEpisode.Status.COMPLETED)

    val ep2 = ep.last()
    assertThat(ep2.commencedAt).isEqualTo(updatedStays.last().arrivedAt)
    assertThat(ep2.concludedAt).isEqualTo(updatedStays.last().departedAt)
    assertThat(ep2.status).isEqualTo(CustodialEpisode.Status.COMPLETED)

    val journeys = journeyRepository.findAllByPersonIdentifierOrderByCommencedAtAsc(personIdentifier)
    assertThat(journeys).isEmpty()
  }

  @Test
  fun `handles transfer in-transit where arrival has not yet occurred`() {
    val personIdentifier = personIdentifier()
    val start = LocalDate.now().atTime(10, 0).minusDays(5)
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
    val trDep = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.TRANSFER,
        origin = prisonOne,
        destination = prisonTwo,
        occurredAt = admission.occurredAt.plusDays(2),
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))

    val stays = stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)
    assertThat(stays).hasSize(1)
    with(stays.single()) {
      assertThat(arrivedAt).isEqualTo(admission.occurredAt)
      assertThat(departedAt).isEqualTo(trDep.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.TRANSFERRED_OUT)
    }

    val episode = episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(personIdentifier).single()
    assertThat(episode.commencedAt).isEqualTo(admission.occurredAt)
    assertThat(episode.concludedAt).isNull()
    assertThat(episode.status).isEqualTo(CustodialEpisode.Status.TEMPORARY_ABSENCE)

    val journeys = journeyRepository.findAllByPersonIdentifierOrderByCommencedAtAsc(personIdentifier)
    assertThat(journeys).hasSize(1)
    with(journeys.single()) {
      assertThat(type).isEqualTo(ExternalJourney.Type.TRANSFER)
      assertThat(status).isEqualTo(ExternalJourney.Status.IN_TRANSIT)
      assertThat(origin).isEqualTo(prisonOne.code)
      assertThat(destination).isEqualTo(prisonTwo.code)
    }
  }

  @Test
  fun `recalculates stay and journey intervals when an intermediate movement is inserted retrospectively`() {
    val personIdentifier = personIdentifier()
    val start = LocalDate.now().atTime(10, 0).minusDays(10)
    val series = givenSeries(custodialSeries(personIdentifier, openedAt = start))
    val prisonOne = prison()

    val admission = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        destination = prisonOne,
        occurredAt = start,
      ),
    )
    val rel = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        origin = prisonOne,
        occurredAt = admission.occurredAt.plusDays(6),
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))

    val tapDep = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.TEMPORARY_ABSENCE,
        origin = prisonOne,
        scheduleReference = externalReference(ExternalReferenceEntity.TEMPORARY_ABSENCE),
        occurredAt = admission.occurredAt.plusDays(2),
      ),
    )
    givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.TEMPORARY_ABSENCE,
        destination = prisonOne,
        occurredAt = tapDep.occurredAt.plusHours(6),
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier, tapDep.occurredAt))

    val stays = stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)
    assertThat(stays).hasSize(1)
    with(stays.single()) {
      assertThat(arrivedAt).isEqualTo(admission.occurredAt)
      assertThat(departedAt).isEqualTo(rel.occurredAt)
    }

    val journeys = journeyRepository.findAllByPersonIdentifierOrderByCommencedAtAsc(personIdentifier)
    assertThat(journeys).hasSize(1)
    assertThat(journeys.map { it.type }).containsExactly(ExternalJourney.Type.TEMPORARY_ABSENCE)
  }

  @Test
  fun `cleans up stays when intermediate movements are deleted`() {
    val personIdentifier = personIdentifier()
    val start = LocalDate.now().atTime(10, 0).minusDays(10)
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
    val trDep = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.TRANSFER,
        origin = prisonOne,
        destination = prisonTwo,
        occurredAt = admission.occurredAt.plusDays(2),
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

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))
    assertThat(stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)).hasSize(2)

    removeMovement(trArr.id)
    removeMovement(trDep.id)

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier, admission.occurredAt))

    val stays = stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)

    assertThat(stays).hasSize(1)
    with(stays.single()) {
      assertThat(prisonCode).isEqualTo(prisonOne.code)
      assertThat(departedAt).isNull()
      assertThat(status).isEqualTo(PrisonStay.Status.RESIDENT)
    }
  }

  @Test
  fun `handles court appearance round trip returning to the same prison without closing the stay`() {
    val personIdentifier = personIdentifier()
    val start = LocalDate.now().atTime(10, 0).minusDays(5)
    val series = givenSeries(custodialSeries(personIdentifier, openedAt = start))
    val prisonOne = prison()

    val admission = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        destination = prisonOne,
        occurredAt = start,
      ),
    )
    val courtDep = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.COURT_APPEARANCE,
        origin = prisonOne,
        scheduleReference = externalReference(ExternalReferenceEntity.COURT_APPEARANCE),
        occurredAt = admission.occurredAt.plusDays(1),
      ),
    )
    givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.COURT_APPEARANCE,
        destination = prisonOne,
        occurredAt = courtDep.occurredAt.plusHours(8),
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))

    val stays = stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)
    assertThat(stays).hasSize(1)
    with(stays.single()) {
      assertThat(status).isEqualTo(PrisonStay.Status.RESIDENT)
      assertThat(departedAt).isNull()
    }

    val journeys = journeyRepository.findAllByPersonIdentifierOrderByCommencedAtAsc(personIdentifier)
    assertThat(journeys).hasSize(1)
    with(journeys.single()) {
      assertThat(type).isEqualTo(ExternalJourney.Type.COURT_APPEARANCE)
      assertThat(status).isEqualTo(ExternalJourney.Status.COMPLETED)
      assertThat(origin).isEqualTo(prisonOne.code)
      assertThat(destination).isEqualTo(prisonOne.code)
    }
  }

  @Test
  fun `creates active stay and active episode for initial admission with no subsequent movements`() {
    val personIdentifier = personIdentifier()
    val start = LocalDate.now().atTime(9, 30).minusDays(1)
    val series = givenSeries(custodialSeries(personIdentifier, openedAt = start))
    val prisonOne = prison()

    val admission = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        destination = prisonOne,
        occurredAt = start,
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))

    val stays = stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)
    assertThat(stays).hasSize(1)
    with(stays.single()) {
      assertThat(arrivedAt).isEqualTo(admission.occurredAt)
      assertThat(departedAt).isNull()
      assertThat(status).isEqualTo(PrisonStay.Status.RESIDENT)
    }
    val episode = episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(personIdentifier).single()
    assertThat(episode.commencedAt).isEqualTo(admission.occurredAt)
    assertThat(episode.concludedAt).isNull()
    assertThat(episode.status).isEqualTo(CustodialEpisode.Status.ACTIVE)
  }

  @Test
  fun `UAL ends stay but not episode - status updated`() {
    val personIdentifier = personIdentifier()
    val start = LocalDate.now().atTime(9, 30).minusDays(1)
    val series = givenSeries(custodialSeries(personIdentifier, openedAt = start))
    val prisonOne = prison()
    val admission = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        destination = prisonOne,
        occurredAt = start,
      ),
    )
    val ual = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        reason = CodedReason("UAL", "Unlawfully at large"),
        origin = prisonOne,
        destination = null,
        occurredAt = start.plusHours(4),
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))

    val stays = stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)
    assertThat(stays).hasSize(1)
    with(stays.single()) {
      assertThat(arrivedAt).isEqualTo(admission.occurredAt)
      assertThat(departedAt).isEqualTo(ual.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
    }
    val episode = episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(personIdentifier).single()
    assertThat(episode.commencedAt).isEqualTo(admission.occurredAt)
    assertThat(episode.concludedAt).isNull()
    assertThat(episode.status).isEqualTo(CustodialEpisode.Status.UNLAWFULLY_AT_LARGE)
  }

  @Test
  fun `UAL custodial episode ended by subsequent release`() {
    val personIdentifier = personIdentifier()
    val start = LocalDate.now().atTime(9, 30).minusDays(90)
    val series = givenSeries(custodialSeries(personIdentifier, openedAt = start))
    val prisonOne = prison()
    val admission = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        destination = prisonOne,
        occurredAt = start,
      ),
    )
    val ual = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        reason = CodedReason("UAL", "Unlawfully at large"),
        origin = prisonOne,
        destination = null,
        occurredAt = start.plusDays(4),
      ),
    )
    val release = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        reason = CodedReason("RE", "Repatriated via prison transfer"),
        origin = prisonOne,
        destination = SimpleLocation("OUTSIDE"),
        occurredAt = start.plusWeeks(12),
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))

    val stays = stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)
    assertThat(stays).hasSize(1)
    with(stays.single()) {
      assertThat(arrivedAt).isEqualTo(admission.occurredAt)
      assertThat(departedAt).isEqualTo(ual.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
    }
    val episode = episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(personIdentifier).single()
    assertThat(episode.commencedAt).isEqualTo(admission.occurredAt)
    assertThat(episode.concludedAt).isEqualTo(release.occurredAt)
    assertThat(episode.status).isEqualTo(CustodialEpisode.Status.COMPLETED)
  }

  @Test
  fun `Death in custody recorded correctly`() {
    val personIdentifier = personIdentifier()
    val start = LocalDate.now().atTime(9, 30).minusDays(1)
    val series = givenSeries(custodialSeries(personIdentifier, openedAt = start))
    val prisonOne = prison()
    val admission = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        destination = prisonOne,
        occurredAt = start,
      ),
    )
    val death = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        reason = CodedReason("DEC", "Death in custody"),
        origin = prisonOne,
        destination = null,
        occurredAt = start.plusHours(4),
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))

    val stays = stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)
    assertThat(stays).hasSize(1)
    with(stays.single()) {
      assertThat(arrivedAt).isEqualTo(admission.occurredAt)
      assertThat(departedAt).isEqualTo(death.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
    }
    val episode = episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(personIdentifier).single()
    assertThat(episode.commencedAt).isEqualTo(admission.occurredAt)
    assertThat(episode.concludedAt).isEqualTo(death.occurredAt)
    assertThat(episode.status).isEqualTo(CustodialEpisode.Status.DECEASED)
  }

  @Test
  fun `can handle release for merge`() {
    val personIdentifier = personIdentifier()
    val start = LocalDate.now().atTime(10, 0).minusWeeks(14)
    val prisonOne = prison()
    val seriesOne = givenSeries(
      custodialSeries(
        personIdentifier,
        openedAt = start,
        closedAt = start.plusDays(7).withHour(0).withMinute(0),
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
        occurredAt = start.plusDays(7),
        reason = CodedReason("MRG", "Administrative release due to merge"),
      ),
    )
    val seriesTwo = givenSeries(
      custodialSeries(
        personIdentifier,
        openedAt = relOne.occurredAt.minusSeconds(30),
      ),
    )
    givenMovement(
      externalMovement(
        seriesTwo,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        destination = prisonOne,
        occurredAt = relOne.occurredAt.minusSeconds(30),
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))

    val stays = stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)
    assertThat(stays).hasSize(1)
    val stay = stays.single()
    assertThat(stay.arrivedAt).isEqualTo(admissionOne.occurredAt)
    assertThat(stay.departedAt).isNull()
    assertThat(stay.status).isEqualTo(PrisonStay.Status.RESIDENT)

    val episodes = episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(personIdentifier).reversed()
    assertThat(episodes).hasSize(1)
    val episodeOne = episodes.single()
    assertThat(episodeOne.commencedAt).isEqualTo(stay.arrivedAt)
    assertThat(episodeOne.concludedAt).isNull()
    assertThat(episodeOne.status).isEqualTo(CustodialEpisode.Status.ACTIVE)

    val journeys = journeyRepository.findAllByPersonIdentifierOrderByCommencedAtAsc(personIdentifier)
    assertThat(journeys).isEmpty()
  }

  @Test
  fun `MRG release on same series with no later movement closes stay and episode`() {
    val personIdentifier = personIdentifier()
    val start = LocalDate.now().atTime(10, 0).minusDays(3)
    val series = givenSeries(custodialSeries(personIdentifier, openedAt = start, legacyId = newId()))
    val prisonOne = prison()

    val admission = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        destination = prisonOne,
        occurredAt = start,
        legacyId = "${series.legacyId}_1",
      ),
    )
    val mrgRelease = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        origin = prisonOne,
        occurredAt = admission.occurredAt.plusHours(6),
        reason = CodedReason("MRG", "Administrative release due to merge"),
        legacyId = "${series.legacyId}_2",
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))

    val stay = stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier).single()
    assertThat(stay.arrivedAt).isEqualTo(admission.occurredAt)
    assertThat(stay.departedAt).isEqualTo(mrgRelease.occurredAt)
    assertThat(stay.status).isEqualTo(PrisonStay.Status.RELEASED)

    val episode = episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(personIdentifier).single()
    assertThat(episode.commencedAt).isEqualTo(admission.occurredAt)
    assertThat(episode.concludedAt).isEqualTo(mrgRelease.occurredAt)
    assertThat(episode.status).isEqualTo(CustodialEpisode.Status.COMPLETED)
  }

  @Test
  fun `MRG release on different series with no later movement does not close stay or episode`() {
    val personIdentifier = personIdentifier()
    val start = LocalDate.now().atTime(10, 0).minusDays(7)
    val prisonOne = prison()

    val seriesOne = givenSeries(custodialSeries(personIdentifier, openedAt = start, legacyId = newId()))
    val admissionOne = givenMovement(
      externalMovement(
        seriesOne,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        destination = prisonOne,
        occurredAt = start,
        legacyId = "${seriesOne.legacyId}_1",
      ),
    )
    val seriesTwo = givenSeries(
      custodialSeries(
        personIdentifier,
        openedAt = start.plusDays(2),
        legacyId = newId(),
      ),
    )
    givenMovement(
      externalMovement(
        seriesTwo,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        origin = prisonOne,
        occurredAt = start.plusDays(2),
        reason = CodedReason("MRG", "Administrative release due to merge"),
        legacyId = "${seriesTwo.legacyId}_1",
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))

    val stay = stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier).single()
    assertThat(stay.arrivedAt).isEqualTo(admissionOne.occurredAt)
    assertThat(stay.departedAt).isNull()
    assertThat(stay.status).isEqualTo(PrisonStay.Status.RESIDENT)

    val episode = episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(personIdentifier).single()
    assertThat(episode.commencedAt).isEqualTo(admissionOne.occurredAt)
    assertThat(episode.concludedAt).isNull()
    assertThat(episode.status).isEqualTo(CustodialEpisode.Status.ACTIVE)
  }

  @Test
  fun `recapture arrival after UAL restores active episode and opens new stay`() {
    val personIdentifier = personIdentifier()
    val start = LocalDate.now().atTime(9, 0).minusDays(10)
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
    val ual = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        reason = CodedReason("UAL", "Unlawfully at large"),
        origin = prisonOne,
        occurredAt = start.plusDays(2),
      ),
    )
    val recapture = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        destination = prisonTwo,
        occurredAt = ual.occurredAt.plusDays(3),
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))

    val stays = stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)
    assertThat(stays).hasSize(2)
    with(stays.first()) {
      assertThat(arrivedAt).isEqualTo(admission.occurredAt)
      assertThat(departedAt).isEqualTo(ual.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
    }
    with(stays.last()) {
      assertThat(prisonCode).isEqualTo(prisonTwo.code)
      assertThat(arrivedAt).isEqualTo(recapture.occurredAt)
      assertThat(departedAt).isNull()
      assertThat(status).isEqualTo(PrisonStay.Status.RESIDENT)
    }

    val episode = episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(personIdentifier).single()
    assertThat(episode.commencedAt).isEqualTo(admission.occurredAt)
    assertThat(episode.concludedAt).isNull()
    assertThat(episode.status).isEqualTo(CustodialEpisode.Status.ACTIVE)
  }

  @Test
  fun `deleting admission removes stays, episodes, and journeys`() {
    val personIdentifier = personIdentifier()
    val start = LocalDate.now().atTime(10, 0).minusDays(4)
    val series = givenSeries(custodialSeries(personIdentifier, openedAt = start))
    val prisonOne = prison()

    val admission = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        destination = prisonOne,
        occurredAt = start,
      ),
    )
    givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        origin = prisonOne,
        occurredAt = start.plusDays(2),
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))
    assertThat(stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)).hasSize(1)

    removeMovement(admission.id)
    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))

    assertThat(stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)).isEmpty()
    assertThat(episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(personIdentifier)).isEmpty()
    assertThat(journeyRepository.findAllByPersonIdentifierOrderByCommencedAtAsc(personIdentifier)).isEmpty()
  }

  @Test
  fun `absorbs overlapping arrivals and collapses duplicate releases at the same prison`() {
    val personIdentifier = personIdentifier()
    val prison = prison()
    val firstArrivalTime = LocalDateTime.of(2026, 8, 24, 11, 0)
    val secondArrivalTime = LocalDateTime.of(2026, 8, 25, 10, 0)
    val firstReleaseTime = LocalDateTime.of(2026, 8, 25, 11, 0)
    val secondReleaseTime = LocalDateTime.of(2026, 9, 12, 9, 0)

    val seriesOne = givenSeries(custodialSeries(personIdentifier, openedAt = firstArrivalTime))
    val seriesTwo = givenSeries(custodialSeries(personIdentifier, openedAt = secondArrivalTime))

    givenMovement(
      externalMovement(
        seriesOne,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        destination = prison,
        occurredAt = firstArrivalTime,
      ),
    )
    givenMovement(
      externalMovement(
        seriesTwo,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        destination = prison,
        occurredAt = secondArrivalTime,
      ),
    )
    givenMovement(
      externalMovement(
        seriesOne,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        origin = prison,
        occurredAt = firstReleaseTime,
      ),
    )
    givenMovement(
      externalMovement(
        seriesTwo,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        origin = prison,
        occurredAt = secondReleaseTime,
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))

    val stays = stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)
    assertThat(stays).hasSize(1)
    with(stays.single()) {
      assertThat(prisonCode).isEqualTo(prison.code)
      assertThat(arrivedAt).isEqualTo(firstArrivalTime)
      assertThat(departedAt).isEqualTo(secondReleaseTime)
      assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
    }

    val episode = episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(personIdentifier).single()
    assertThat(episode.commencedAt).isEqualTo(firstArrivalTime)
    assertThat(episode.concludedAt).isEqualTo(secondReleaseTime)
    assertThat(episode.status).isEqualTo(CustodialEpisode.Status.COMPLETED)
  }

  @Test
  fun `records transfer instead of collapsing departures when arrival at another prison occurs in between`() {
    val personIdentifier = personIdentifier()
    val prisonOne = prison()
    val prisonTwo = prison()
    val start = LocalDate.now().atTime(9, 0).minusDays(12)
    val series = givenSeries(custodialSeries(personIdentifier, openedAt = start))

    val admissionPrisonOne = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        destination = prisonOne,
        occurredAt = start,
      ),
    )
    val departurePrisonOne = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        origin = prisonOne,
        occurredAt = admissionPrisonOne.occurredAt.plusDays(2),
      ),
    )
    val arrivalPrisonTwo = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        destination = prisonTwo,
        occurredAt = departurePrisonOne.occurredAt.plusHours(3),
      ),
    )
    val releasePrisonTwo = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        origin = prisonTwo,
        occurredAt = arrivalPrisonTwo.occurredAt.plusDays(4),
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))

    val stays = stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)
    assertThat(stays).hasSize(2)
    with(stays.first()) {
      assertThat(prisonCode).isEqualTo(prisonOne.code)
      assertThat(arrivedAt).isEqualTo(admissionPrisonOne.occurredAt)
      assertThat(departedAt).isEqualTo(departurePrisonOne.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
    }
    with(stays.last()) {
      assertThat(prisonCode).isEqualTo(prisonTwo.code)
      assertThat(arrivedAt).isEqualTo(arrivalPrisonTwo.occurredAt)
      assertThat(departedAt).isEqualTo(releasePrisonTwo.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
    }
  }

  @Test
  fun `can handle genuine release and readmission to the same prison`() {
    val personIdentifier = personIdentifier()
    val start = LocalDate.now().atTime(10, 0).minusDays(14)
    val series = givenSeries(custodialSeries(personIdentifier, openedAt = start))
    val prison = prison()
    val ad1 = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        destination = prison,
        occurredAt = start,
      ),
    )
    val rel1 = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        destination = prison,
        occurredAt = start.plusDays(1),
      ),
    )
    val ad2 = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        destination = prison,
        occurredAt = start.plusDays(3),
      ),
    )
    val rel2 = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        destination = prison,
        occurredAt = start.plusDays(4),
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))

    val stays = stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)
    assertThat(stays).hasSize(2)
    with(stays.first()) {
      assertThat(arrivedAt).isEqualTo(ad1.occurredAt)
      assertThat(departedAt).isEqualTo(rel1.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
    }
    with(stays.last()) {
      assertThat(arrivedAt).isEqualTo(ad2.occurredAt)
      assertThat(departedAt).isEqualTo(rel2.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
    }

    val episodes = episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(personIdentifier).reversed()
    assertThat(episodes).hasSize(2)
    with(episodes.first()) {
      assertThat(commencedAt).isEqualTo(ad1.occurredAt)
      assertThat(concludedAt).isEqualTo(rel1.occurredAt)
      assertThat(status).isEqualTo(CustodialEpisode.Status.COMPLETED)
    }
    with(episodes.last()) {
      assertThat(commencedAt).isEqualTo(ad2.occurredAt)
      assertThat(concludedAt).isEqualTo(rel2.occurredAt)
      assertThat(status).isEqualTo(CustodialEpisode.Status.COMPLETED)
    }

    val journeys = journeyRepository.findAllByPersonIdentifierOrderByCommencedAtAsc(personIdentifier)
    assertThat(journeys).isEmpty()
  }

  @Test
  fun `concludes journey at release and ignores late temporary absence arrival after release`() {
    val personIdentifier = personIdentifier()
    val series = givenSeries(custodialSeries(personIdentifier, openedAt = LocalDateTime.of(2009, 4, 30, 16, 5, 26)))
    val prisonOne = prison()

    val admission = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        destination = prisonOne,
        occurredAt = LocalDateTime.of(2025, 4, 30, 16, 5, 26),
      ),
    )
    val tapDep = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.TEMPORARY_ABSENCE,
        origin = prisonOne,
        scheduleReference = externalReference(ExternalReferenceEntity.TEMPORARY_ABSENCE),
        occurredAt = LocalDateTime.of(2025, 6, 4, 9, 30, 0),
      ),
    )
    val rel = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        origin = prisonOne,
        occurredAt = LocalDateTime.of(2025, 7, 3, 17, 45, 9),
      ),
    )
    givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.TEMPORARY_ABSENCE,
        destination = prisonOne,
        occurredAt = LocalDateTime.of(2025, 7, 3, 17, 45, 52),
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))

    val stays = stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)
    assertThat(stays).hasSize(1)
    with(stays.single()) {
      assertThat(arrivedAt).isEqualTo(admission.occurredAt)
      assertThat(departedAt).isEqualTo(rel.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
    }

    val episode = episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(personIdentifier).single()
    assertThat(episode.commencedAt).isEqualTo(admission.occurredAt)
    assertThat(episode.concludedAt).isEqualTo(rel.occurredAt)
    assertThat(episode.status).isEqualTo(CustodialEpisode.Status.COMPLETED)

    val journeys = journeyRepository.findAllByPersonIdentifierOrderByCommencedAtAsc(personIdentifier)
    assertThat(journeys).hasSize(1)
    with(journeys.single()) {
      assertThat(type).isEqualTo(ExternalJourney.Type.TEMPORARY_ABSENCE)
      assertThat(externalReference).isEqualTo(tapDep.scheduleReference!!)
      assertThat(commencedAt).isEqualTo(tapDep.occurredAt)
      assertThat(concludedAt).isEqualTo(rel.occurredAt)
      assertThat(origin).isEqualTo(prisonOne.code)
      assertThat(destination).isNull()
    }
  }

  @Test
  fun `final MRG release after booking merge closes stay and episode`() {
    val personIdentifier = personIdentifier()
    val prisonOne = prison()

    val seriesOne = givenSeries(
      custodialSeries(
        personIdentifier,
        openedAt = LocalDateTime.of(2024, 11, 15, 14, 18, 41),
        closedAt = LocalDateTime.of(2025, 7, 18, 14, 31, 27),
        status = CustodialSeries.Status.CLOSED,
        isActive = false,
        legacyId = newId(),
      ),
    )
    val seriesTwo = givenSeries(
      custodialSeries(
        personIdentifier,
        openedAt = LocalDateTime.of(2024, 11, 20, 18, 18, 6),
        legacyId = newId(),
      ),
    )

    // Episode 1: 2024-11-15 to 2025-07-18 (includes merge and court round trip)
    val ep1Admission = givenMovement(
      externalMovement(
        seriesOne,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        reason = CodedReason("N", "Unconvicted Remand"),
        destination = prisonOne,
        occurredAt = LocalDateTime.of(2024, 11, 15, 14, 18, 41),
        legacyId = "${seriesOne.legacyId}_1",
      ),
    )
    givenMovement(
      externalMovement(
        seriesTwo,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        reason = CodedReason("L", "Licence Revokee"),
        destination = prisonOne,
        occurredAt = LocalDateTime.of(2024, 11, 20, 18, 18, 6),
        legacyId = "${seriesTwo.legacyId}_1",
      ),
    )
    givenMovement(
      externalMovement(
        seriesOne,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        reason = CodedReason("MRG", "Administrative Release due to Merge"),
        origin = prisonOne,
        occurredAt = LocalDateTime.of(2024, 11, 20, 18, 18, 47),
        legacyId = "${seriesOne.legacyId}_2",
      ),
    )
    givenMovement(
      externalMovement(
        seriesTwo,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.COURT_APPEARANCE,
        reason = CodedReason("SENT", "Sentencing"),
        origin = prisonOne,
        scheduleReference = externalReference(ExternalReferenceEntity.COURT_APPEARANCE),
        occurredAt = LocalDateTime.of(2025, 7, 4, 9, 9, 0),
        legacyId = "${seriesTwo.legacyId}_2",
      ),
    )
    givenMovement(
      externalMovement(
        seriesTwo,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.COURT_APPEARANCE,
        reason = CodedReason("SENT", "Sentencing"),
        destination = prisonOne,
        occurredAt = LocalDateTime.of(2025, 7, 4, 15, 6, 0),
        legacyId = "${seriesTwo.legacyId}_3",
      ),
    )
    val ep1Release = givenMovement(
      externalMovement(
        seriesTwo,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        reason = CodedReason("CR", "CONDITIONAL RELEASE"),
        origin = prisonOne,
        occurredAt = LocalDateTime.of(2025, 7, 18, 14, 31, 27),
        legacyId = "${seriesTwo.legacyId}_4",
      ),
    )

    // Episode 2: 2025-07-28 to 2026-02-24 (booking merge, then final MRG release)
    val seriesThree = givenSeries(
      custodialSeries(
        personIdentifier,
        openedAt = LocalDateTime.of(2025, 7, 28, 14, 25, 46),
        closedAt = LocalDateTime.of(2025, 7, 29, 9, 43, 22),
        status = CustodialSeries.Status.CLOSED,
        isActive = false,
        legacyId = newId(),
      ),
    )
    val ep2Admission = givenMovement(
      externalMovement(
        seriesThree,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        reason = CodedReason("N", "Unconvicted Remand"),
        destination = prisonOne,
        occurredAt = LocalDateTime.of(2025, 7, 28, 14, 25, 46),
        legacyId = "${seriesThree.legacyId}_1",
      ),
    )
    givenMovement(
      externalMovement(
        seriesThree,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        reason = CodedReason("MRG", "Administrative Release due to Merge"),
        origin = prisonOne,
        occurredAt = LocalDateTime.of(2025, 7, 29, 9, 43, 22),
        legacyId = "${seriesThree.legacyId}_2",
      ),
    )
    givenMovement(
      externalMovement(
        seriesTwo,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        reason = CodedReason("L", "Licence Revokee"),
        destination = prisonOne,
        occurredAt = LocalDateTime.of(2025, 7, 29, 9, 43, 33),
        legacyId = "${seriesTwo.legacyId}_5",
      ),
    )
    givenMovement(
      externalMovement(
        seriesTwo,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.COURT_APPEARANCE,
        reason = CodedReason("CRT", "Court Appearance"),
        origin = prisonOne,
        scheduleReference = externalReference(ExternalReferenceEntity.COURT_APPEARANCE),
        occurredAt = LocalDateTime.of(2026, 1, 20, 7, 56, 0),
        legacyId = "${seriesTwo.legacyId}_10",
      ),
    )
    givenMovement(
      externalMovement(
        seriesTwo,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.COURT_APPEARANCE,
        reason = CodedReason("CRT", "Court Appearance"),
        destination = prisonOne,
        occurredAt = LocalDateTime.of(2026, 1, 20, 15, 34, 0),
        legacyId = "${seriesTwo.legacyId}_11",
      ),
    )
    val finalMrgRelease = givenMovement(
      externalMovement(
        seriesTwo,
        movementType = ExternalMovement.Type.DEPARTURE,
        journeyType = ExternalJourney.Type.RELEASE,
        reason = CodedReason("MRG", "Administrative Release due to Merge"),
        origin = prisonOne,
        occurredAt = LocalDateTime.of(2026, 2, 24, 13, 51, 46),
        legacyId = "${seriesTwo.legacyId}_12",
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))

    val stays = stayRepository.findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)
    assertThat(stays).hasSize(2)
    with(stays.first()) {
      assertThat(arrivedAt).isEqualTo(ep1Admission.occurredAt)
      assertThat(departedAt).isEqualTo(ep1Release.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
    }
    with(stays.last()) {
      assertThat(arrivedAt).isEqualTo(ep2Admission.occurredAt)
      assertThat(departedAt).isEqualTo(finalMrgRelease.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
    }

    val episodes = episodeRepository.findAllByPersonIdentifierOrderByCommencedAtDesc(personIdentifier).reversed()
    assertThat(episodes).hasSize(2)
    with(episodes.first()) {
      assertThat(commencedAt).isEqualTo(ep1Admission.occurredAt)
      assertThat(concludedAt).isEqualTo(ep1Release.occurredAt)
      assertThat(status).isEqualTo(CustodialEpisode.Status.COMPLETED)
    }
    with(episodes.last()) {
      assertThat(commencedAt).isEqualTo(ep2Admission.occurredAt)
      assertThat(concludedAt).isEqualTo(finalMrgRelease.occurredAt)
      assertThat(status).isEqualTo(CustodialEpisode.Status.COMPLETED)
    }
  }
}
