package uk.gov.justice.digital.hmpps.personlocationapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.personlocationapi.access.Roles
import uk.gov.justice.digital.hmpps.personlocationapi.event.internal.RefreshPersonMovementIntervals
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.externalReference
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialSeriesOperations
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialSeriesOperationsImpl.Companion.custodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperations
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperationsImpl.Companion.externalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperationsImpl.Companion.prison
import uk.gov.justice.digital.hmpps.personlocationapi.model.timeline.TimeLineActivity
import uk.gov.justice.digital.hmpps.personlocationapi.model.timeline.Timeline
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalJourney
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.externalreference.ExternalReferenceEntity
import java.time.LocalDate
import java.time.LocalDateTime

class RetrieveTimelineIntTest(
  @Autowired serOps: CustodialSeriesOperations,
  @Autowired moveOps: ExternalMovementOperations,
) : IntegrationTest(),
  CustodialSeriesOperations by serOps,
  ExternalMovementOperations by moveOps {

  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .get()
      .uri(RETRIEVE_TIMELINE, personIdentifier())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    retrieveTimeline(personIdentifier(), "ROLE_ANY__OTHER__RW").expectStatus().isForbidden
  }

  @Test
  fun `200 ok no periods of custody for a given person`() {
    val personIdentifier = personIdentifier()
    val res = retrieveTimeline(personIdentifier).successResponse<Timeline>()
    assertThat(res.periods).isEmpty()
  }

  @Test
  fun `timeline with single episode`() {
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

    val res = retrieveTimeline(personIdentifier).successResponse<Timeline>()
    assertThat(res.periods).hasSize(1)
    with(res.periods.single()) {
      assertThat(commencedAt).isEqualTo(admission.occurredAt)
      assertThat(concludedAt).isEqualTo(rel.occurredAt)
      assertThat(stays).hasSize(2)
      with(stays.first()) {
        assertThat(prison.code).isEqualTo(prisonOne.code)
        assertThat(arrival).isEqualTo(TimeLineActivity(admission.occurredAt, admission.reason.description))
        assertThat(departure).isEqualTo(TimeLineActivity(trDep.occurredAt, trDep.reason.description))
      }
      with(stays.last()) {
        assertThat(prison.code).isEqualTo(prisonTwo.code)
        assertThat(arrival).isEqualTo(TimeLineActivity(trArr.occurredAt, trArr.reason.description))
        assertThat(departure).isEqualTo(TimeLineActivity(rel.occurredAt, rel.reason.description))
      }
    }
  }

  @Test
  fun `timeline with multiple episodes`() {
    val personIdentifier = personIdentifier()
    val start = LocalDate.now().atTime(10, 0).minusDays(14)
    val series = givenSeries(custodialSeries(personIdentifier, openedAt = start))
    val prisonOne = prison()
    val prisonTwo = prison()
    val prisonThree = prison()
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
    val newSeries = givenSeries(custodialSeries(personIdentifier, openedAt = LocalDateTime.now().minusDays(3)))
    val current = givenMovement(
      externalMovement(
        newSeries,
        movementType = ExternalMovement.Type.ARRIVAL,
        journeyType = ExternalJourney.Type.ADMISSION,
        destination = prisonThree,
        occurredAt = newSeries.openedAt,
      ),
    )

    refresh.execute(RefreshPersonMovementIntervals(personIdentifier))

    val res = retrieveTimeline(personIdentifier).successResponse<Timeline>()
    assertThat(res.periods).hasSize(2)
    with(res.periods.first()) {
      assertThat(commencedAt).isEqualTo(admission.occurredAt)
      assertThat(concludedAt).isEqualTo(rel.occurredAt)
      assertThat(stays).hasSize(2)
      with(stays.first()) {
        assertThat(prison.code).isEqualTo(prisonOne.code)
        assertThat(arrival).isEqualTo(TimeLineActivity(admission.occurredAt, admission.reason.description))
        assertThat(departure).isEqualTo(TimeLineActivity(trDep.occurredAt, trDep.reason.description))
      }
      with(stays.last()) {
        assertThat(prison.code).isEqualTo(prisonTwo.code)
        assertThat(arrival).isEqualTo(TimeLineActivity(trArr.occurredAt, trArr.reason.description))
        assertThat(departure).isEqualTo(TimeLineActivity(rel.occurredAt, rel.reason.description))
      }
    }
    with(res.periods.last()) {
      assertThat(commencedAt).isEqualTo(current.occurredAt)
      assertThat(concludedAt).isNull()
      assertThat(stays).hasSize(1)
      with(stays.single()) {
        assertThat(prison.code).isEqualTo(prisonThree.code)
        assertThat(arrival).isEqualTo(TimeLineActivity(current.occurredAt, current.reason.description))
        assertThat(departure).isNull()
      }
    }
  }

  private fun retrieveTimeline(
    personIdentifier: String,
    role: String? = Roles.PERSON_PERIODS_RO,
  ) = webTestClient
    .get()
    .uri(RETRIEVE_TIMELINE, personIdentifier)
    .headers(setAuthorisation(username = DEFAULT_USERNAME, roles = listOfNotNull(role)))
    .exchange()

  companion object {
    const val RETRIEVE_TIMELINE = "/integrations/people/{personIdentifier}/timeline"
  }
}
