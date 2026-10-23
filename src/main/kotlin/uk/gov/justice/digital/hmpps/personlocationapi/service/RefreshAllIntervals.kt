package uk.gov.justice.digital.hmpps.personlocationapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.personlocationapi.event.internal.InternalEventEmitter
import uk.gov.justice.digital.hmpps.personlocationapi.event.internal.RefreshPersonMovementIntervals
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeriesRepository

@Service
class RefreshAllIntervals(
  private val csRepository: CustodialSeriesRepository,
  private val iee: InternalEventEmitter,
) {
  fun execute() {
    iee.publishInternalEvents(csRepository.findPersonIdentifiers().map { RefreshPersonMovementIntervals(it) })
  }
}
