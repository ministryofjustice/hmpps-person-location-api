package uk.gov.justice.digital.hmpps.personlocationapi.integration.sync

import org.assertj.core.api.Assertions.assertThat
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement
import java.time.temporal.ChronoUnit
import uk.gov.justice.digital.hmpps.personlocationapi.sync.CustodialSeries as SyncCustodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ExternalMovement as SyncExternalMovement

infix fun CustodialSeries.verifyAgainst(request: SyncCustodialSeries) {
  assertThat(status.name).isEqualTo(request.status.name)
  assertThat(isActive).isEqualTo(request.isActive)
  assertThat(openedAt.truncatedTo(ChronoUnit.SECONDS)).isEqualTo(request.openedAt.truncatedTo(ChronoUnit.SECONDS))
  assertThat(closedAt?.truncatedTo(ChronoUnit.SECONDS)).isEqualTo(request.closedAt?.truncatedTo(ChronoUnit.SECONDS))
  assertThat(notes).isEqualTo(request.notes)
  assertThat(legacyBookingReference).isEqualTo(request.legacyBookingReference)
  assertThat(legacyId).isEqualTo(request.legacyBookingId)
}

infix fun ExternalMovement.verifyAgainst(request: SyncExternalMovement) {
  assertThat(journeyType.name).isEqualTo(request.journey.type.name)
  assertThat(movementType.name).isEqualTo(request.type.name)
  assertThat(occurredAt.truncatedTo(ChronoUnit.SECONDS)).isEqualTo(request.occurredAt.truncatedTo(ChronoUnit.SECONDS))
  assertThat(notes).isEqualTo(request.notes)
  assertThat(reason).isEqualTo(request.reason)
  assertThat(origin).isEqualTo(request.from)
  assertThat(destination).isEqualTo(request.to)
  assertThat(scheduleReference).isEqualTo(request.journey.scheduleReference)
  assertThat(legacyId).isEqualTo(request.legacyId)
}
