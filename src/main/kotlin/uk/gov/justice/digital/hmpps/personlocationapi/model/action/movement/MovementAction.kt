package uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement

import uk.gov.justice.digital.hmpps.personlocationapi.model.action.Action
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement

sealed interface MovementAction : Action<ExternalMovement>
