package uk.gov.justice.digital.hmpps.personlocationapi.sync.internal

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.personlocationapi.context.RequestContext
import uk.gov.justice.digital.hmpps.personlocationapi.context.RequestContext.Companion.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.personlocationapi.context.set
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.DataSource
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ResyncExternalMovementsRequest
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ResyncResponse

@Transactional
@Service
class ResyncExternalMovements(

) {
  fun all(personIdentifier: String, request: ResyncExternalMovementsRequest): ResyncResponse {
    RequestContext.get().copy(username = SYSTEM_USERNAME, source = DataSource.NOMIS, migratingData = true).set()

  }
}