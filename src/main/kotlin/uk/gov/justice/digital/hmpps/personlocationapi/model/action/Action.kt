package uk.gov.justice.digital.hmpps.personlocationapi.model.action

import uk.gov.justice.digital.hmpps.personlocationapi.event.DomainEvent

interface Action<T> {
  fun domainEvent(entity: T): DomainEvent<*>? = null
  infix fun changes(entity: T): Boolean
}
