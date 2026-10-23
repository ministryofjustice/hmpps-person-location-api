package uk.gov.justice.digital.hmpps.personlocationapi.integration.refreshintervals

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.personlocationapi.event.internal.RefreshPersonMovementIntervals
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.externalReference
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.personlocationapi.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialSeriesOperations
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialSeriesOperationsImpl.Companion.custodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperations
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperationsImpl.Companion.externalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperationsImpl.Companion.prison
import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.ApplyOccurredAt
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisode
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalJourney
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalJourneyRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.PrisonStay
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.PrisonStayRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.externalreference.ExternalReferenceEntity
import uk.gov.justice.digital.hmpps.personlocationapi.service.RefreshExternalMovementIntervals
import java.time.LocalDate

class RefreshIntervalsIntTest(
  @Autowired seriesOps: CustodialSeriesOperations,
  @Autowired moveOps: ExternalMovementOperations,
  @Autowired private val refresh: RefreshExternalMovementIntervals,
  @Autowired private val stayRepository: PrisonStayRepository,
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

    val stays = transactionTemplate.execute {
      stayRepository.findAllByPersonIdentifier(personIdentifier).sortedBy { it.arrivedAt }.onEach { it.movements() }
    }
    assertThat(stays).hasSize(2)
    with(stays.first()) {
      assertThat(arrivedAt).isEqualTo(admission.occurredAt)
      assertThat(departedAt).isEqualTo(trDep.occurredAt)
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

    val journeys = transactionTemplate.execute {
      journeyRepository.findFindAllByMovementsIdIn(
        listOf(
          admission.id,
          tapDep.id,
          tapArr.id,
          trDep.id,
          trArr.id,
          courtApp.id,
          rel.id,
        ),
      )
        .sortedBy { it.movements().first().occurredAt }
    }
    assertThat(journeys).hasSize(4)
    with(journeys.first()) {
      assertThat(type).isEqualTo(ExternalJourney.Type.ADMISSION)
      assertThat(movements().map { it.id }).containsExactly(admission.id)
      assertThat(origin).isNull()
      assertThat(destination).isEqualTo(prisonOne.code)
    }
    with(journeys[1]) {
      assertThat(type).isEqualTo(ExternalJourney.Type.TEMPORARY_ABSENCE)
      assertThat(externalReference).isEqualTo(tapDep.scheduleReference!!)
      assertThat(movements().map { it.id }).containsExactly(tapDep.id, tapArr.id)
      assertThat(origin).isEqualTo(prisonOne.code)
      assertThat(destination).isEqualTo(prisonOne.code)
    }
    with(journeys[2]) {
      assertThat(type).isEqualTo(ExternalJourney.Type.TRANSFER)
      assertThat(movements().map { it.id }).containsExactly(trDep.id, trArr.id)
      assertThat(origin).isEqualTo(prisonOne.code)
      assertThat(destination).isEqualTo(prisonTwo.code)
    }
    with(journeys.last()) {
      assertThat(type).isEqualTo(ExternalJourney.Type.COURT_APPEARANCE)
      assertThat(externalReference).isEqualTo(courtApp.scheduleReference!!)
      assertThat(movements().map { it.id }).containsExactly(courtApp.id, rel.id)
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

    val journeys = transactionTemplate.execute {
      journeyRepository.findFindAllByMovementsIdIn(listOf(admissionOne.id, relOne.id, admissionTwo.id, tapDep.id))
        .sortedBy { it.movements().first().occurredAt }
    }
    assertThat(journeys).hasSize(4)
    with(journeys.first()) {
      assertThat(type).isEqualTo(ExternalJourney.Type.ADMISSION)
      assertThat(movements().map { it.id }).containsExactly(admissionOne.id)
      assertThat(origin).isNull()
      assertThat(destination).isEqualTo(prisonOne.code)
    }
    with(journeys[1]) {
      assertThat(type).isEqualTo(ExternalJourney.Type.RELEASE)
      assertThat(movements().map { it.id }).containsExactly(relOne.id)
      assertThat(origin).isEqualTo(prisonOne.code)
      assertThat(destination).isNull()
    }
    with(journeys[2]) {
      assertThat(type).isEqualTo(ExternalJourney.Type.ADMISSION)
      assertThat(movements().map { it.id }).containsExactly(admissionTwo.id)
      assertThat(origin).isNull()
      assertThat(destination).isEqualTo(prisonTwo.code)
    }
    with(journeys.last()) {
      assertThat(type).isEqualTo(ExternalJourney.Type.TEMPORARY_ABSENCE)
      assertThat(externalReference).isEqualTo(tapDep.scheduleReference!!)
      assertThat(status).isEqualTo(ExternalJourney.Status.IN_TRANSIT)
      assertThat(movements().map { it.id }).containsExactly(tapDep.id)
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
      val stays = transactionTemplate.execute {
        stayRepository.findAllByPersonIdentifier(personIdentifier).sortedBy { it.arrivedAt }.onEach { it.movements() }
      }
      assertThat(stays).hasSize(1)
      with(stays.first()) {
        assertThat(arrivedAt).isEqualTo(admission.occurredAt)
        assertThat(departedAt).isEqualTo(rel.occurredAt)
        assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
        assertThat(movements().map { it.id }).containsExactly(admission.id, tapDep.id, tapArr.id, rel.id)
      }
      val episode = stays.first().episode
      assertThat(episode.commencedAt).isEqualTo(stays.first().arrivedAt)
      assertThat(episode.concludedAt).isEqualTo(stays.first().departedAt)
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

    val journeys = transactionTemplate.execute {
      journeyRepository.findFindAllByMovementsIdIn(listOf(admission.id, tapDep.id, tapArr.id, rel.id))
        .sortedBy { it.movements().first().occurredAt }
    }
    assertThat(journeys).hasSize(3)
    with(journeys.first()) {
      assertThat(type).isEqualTo(ExternalJourney.Type.ADMISSION)
      assertThat(movements().map { it.id }).containsExactly(admission.id)
      assertThat(origin).isNull()
      assertThat(destination).isEqualTo(prisonOne.code)
    }
    with(journeys[1]) {
      assertThat(type).isEqualTo(ExternalJourney.Type.TEMPORARY_ABSENCE)
      assertThat(externalReference).isEqualTo(tapDep.scheduleReference!!)
      assertThat(movements().map { it.id }).containsExactly(tapDep.id, tapArr.id)
      assertThat(origin).isEqualTo(prisonOne.code)
      assertThat(destination).isEqualTo(prisonOne.code)
    }
    with(journeys.last()) {
      assertThat(type).isEqualTo(ExternalJourney.Type.RELEASE)
      assertThat(status).isEqualTo(ExternalJourney.Status.COMPLETED)
      assertThat(movements().map { it.id }).containsExactly(rel.id)
      assertThat(origin).isEqualTo(prisonOne.code)
      assertThat(destination).isNull()
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
      assertThat(departedAt).isEqualTo(relTwo.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
      assertThat(movements().map { it.id }).containsExactly(admissionTwo.id, relTwo.id)
    }
    assertThat(stays.first().episode.id).isNotEqualTo(stays.last().episode.id)
    val episodeOne = stays.first().episode
    assertThat(episodeOne.commencedAt).isEqualTo(stays.first().arrivedAt)
    assertThat(episodeOne.concludedAt).isEqualTo(stays.first().departedAt)
    assertThat(episodeOne.status).isEqualTo(CustodialEpisode.Status.COMPLETED)

    val episodeTwo = stays.last().episode
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

    val updatedStays = transactionTemplate.execute {
      stayRepository.findAllByPersonIdentifier(personIdentifier).sortedBy { it.arrivedAt }.onEach { it.movements() }
    }

    assertThat(updatedStays).hasSize(2)
    with(updatedStays.first()) {
      assertThat(arrivedAt).isEqualTo(admissionOne.occurredAt)
      assertThat(departedAt).isEqualTo(relOne.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
      assertThat(movements().map { it.id }).containsExactly(admissionOne.id, relOne.id)
    }
    with(updatedStays.last()) {
      assertThat(arrivedAt).isEqualTo(admissionTwo.occurredAt.plusDays(1))
      assertThat(departedAt).isEqualTo(relTwo.occurredAt.minusDays(1))
      assertThat(status).isEqualTo(PrisonStay.Status.RELEASED)
      assertThat(movements().map { it.id }).containsExactly(admissionTwo.id, relTwo.id)
    }
    assertThat(updatedStays.first().episode.id).isNotEqualTo(updatedStays.last().episode.id)
    val ep1 = updatedStays.first().episode
    assertThat(ep1.commencedAt).isEqualTo(updatedStays.first().arrivedAt)
    assertThat(ep1.concludedAt).isEqualTo(updatedStays.first().departedAt)
    assertThat(ep1.status).isEqualTo(CustodialEpisode.Status.COMPLETED)

    val ep2 = updatedStays.last().episode
    assertThat(ep2.commencedAt).isEqualTo(updatedStays.last().arrivedAt)
    assertThat(ep2.concludedAt).isEqualTo(updatedStays.last().departedAt)
    assertThat(ep2.status).isEqualTo(CustodialEpisode.Status.COMPLETED)

    val journeys = transactionTemplate.execute {
      journeyRepository.findFindAllByMovementsIdIn(listOf(admissionOne.id, relOne.id, admissionTwo.id, relTwo.id))
        .sortedBy { it.movements().first().occurredAt }
    }
    assertThat(journeys).hasSize(4)
    with(journeys.first()) {
      assertThat(type).isEqualTo(ExternalJourney.Type.ADMISSION)
      assertThat(movements().map { it.id }).containsExactly(admissionOne.id)
      assertThat(origin).isNull()
      assertThat(destination).isEqualTo(prisonOne.code)
    }
    with(journeys[1]) {
      assertThat(type).isEqualTo(ExternalJourney.Type.RELEASE)
      assertThat(movements().map { it.id }).containsExactly(relOne.id)
      assertThat(origin).isEqualTo(prisonOne.code)
      assertThat(destination).isNull()
    }
    with(journeys[2]) {
      assertThat(type).isEqualTo(ExternalJourney.Type.ADMISSION)
      assertThat(movements().map { it.id }).containsExactly(admissionTwo.id)
      assertThat(origin).isNull()
      assertThat(destination).isEqualTo(prisonTwo.code)
    }
    with(journeys.last()) {
      assertThat(type).isEqualTo(ExternalJourney.Type.RELEASE)
      assertThat(movements().map { it.id }).containsExactly(relTwo.id)
      assertThat(origin).isEqualTo(prisonTwo.code)
      assertThat(destination).isNull()
    }
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

    val stays = transactionTemplate.execute {
      stayRepository.findAllByPersonIdentifier(personIdentifier).sortedBy { it.arrivedAt }
    }
    assertThat(stays).hasSize(1)
    with(stays.single()) {
      assertThat(arrivedAt).isEqualTo(admission.occurredAt)
      assertThat(departedAt).isEqualTo(trDep.occurredAt)
      assertThat(status).isEqualTo(PrisonStay.Status.TRANSFERRED_OUT)
    }

    val episode = stays.single().episode
    assertThat(episode.commencedAt).isEqualTo(admission.occurredAt)
    assertThat(episode.concludedAt).isNull()
    assertThat(episode.status).isEqualTo(CustodialEpisode.Status.ACTIVE)

    val journeys = transactionTemplate.execute {
      journeyRepository.findFindAllByMovementsIdIn(listOf(admission.id, trDep.id))
        .sortedBy { it.movements().first().occurredAt }
    }
    assertThat(journeys).hasSize(2)
    with(journeys.last()) {
      assertThat(type).isEqualTo(ExternalJourney.Type.TRANSFER)
      assertThat(status).isEqualTo(ExternalJourney.Status.IN_TRANSIT)
      assertThat(origin).isEqualTo(prisonOne.code)
      assertThat(destination).isNull()
      assertThat(movements().map { it.id }).containsExactly(trDep.id)
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
    val tapArr = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.TEMPORARY_ABSENCE,
        destination = prisonOne,
        occurredAt = tapDep.occurredAt.plusHours(6),
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier, tapDep.occurredAt))

    val stays = transactionTemplate.execute {
      stayRepository.findAllByPersonIdentifier(personIdentifier).sortedBy { it.arrivedAt }.onEach { it.movements() }
    }
    assertThat(stays).hasSize(1)
    with(stays.single()) {
      assertThat(arrivedAt).isEqualTo(admission.occurredAt)
      assertThat(departedAt).isEqualTo(rel.occurredAt)
      assertThat(movements().map { it.id }).containsExactly(admission.id, tapDep.id, tapArr.id, rel.id)
    }

    val journeys = transactionTemplate.execute {
      journeyRepository.findFindAllByMovementsIdIn(listOf(admission.id, tapDep.id, tapArr.id, rel.id))
        .sortedBy { it.movements().first().occurredAt }
    }
    assertThat(journeys).hasSize(3)
    assertThat(journeys.map { it.type }).containsExactly(
      ExternalJourney.Type.ADMISSION,
      ExternalJourney.Type.TEMPORARY_ABSENCE,
      ExternalJourney.Type.RELEASE,
    )
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
    assertThat(stayRepository.findAllByPersonIdentifier(personIdentifier)).hasSize(2)

    removeMovement(trArr.id)
    removeMovement(trDep.id)

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier, admission.occurredAt))

    val stays = transactionTemplate.execute {
      stayRepository.findAllByPersonIdentifier(personIdentifier).sortedBy { it.arrivedAt }
    }

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
    val courtArr = givenMovement(
      externalMovement(
        series,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.COURT_APPEARANCE,
        destination = prisonOne,
        occurredAt = courtDep.occurredAt.plusHours(8),
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))

    val stays = transactionTemplate.execute {
      stayRepository.findAllByPersonIdentifier(personIdentifier).sortedBy { it.arrivedAt }.onEach { it.movements() }
    }
    assertThat(stays).hasSize(1)
    with(stays.single()) {
      assertThat(status).isEqualTo(PrisonStay.Status.RESIDENT)
      assertThat(departedAt).isNull()
      assertThat(movements().map { it.id }).containsExactly(admission.id, courtDep.id, courtArr.id)
    }

    val journeys = transactionTemplate.execute {
      journeyRepository.findFindAllByMovementsIdIn(listOf(courtDep.id, courtArr.id))
    }
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

    val stays = transactionTemplate.execute {
      stayRepository.findAllByPersonIdentifier(personIdentifier).sortedBy { it.arrivedAt }
    }
    assertThat(stays).hasSize(1)
    with(stays.single()) {
      assertThat(arrivedAt).isEqualTo(admission.occurredAt)
      assertThat(departedAt).isNull()
      assertThat(status).isEqualTo(PrisonStay.Status.RESIDENT)
    }
    val episode = stays.single().episode
    assertThat(episode.commencedAt).isEqualTo(admission.occurredAt)
    assertThat(episode.concludedAt).isNull()
    assertThat(episode.status).isEqualTo(CustodialEpisode.Status.ACTIVE)
  }
}
