package uk.gov.justice.digital.hmpps.personlocationapi.persistence

import java.time.LocalDateTime

internal fun <E : Any, T : Any> mergeEntities(
  existing: List<E>,
  targets: List<T>,
  entityStartSelector: (E) -> LocalDateTime,
  targetStartSelector: (T) -> LocalDateTime,
  updateFrom: E.(T) -> Unit,
  createEntity: (T) -> E,
  deleteAll: (Iterable<E>) -> Unit,
  saveAll: (Iterable<E>) -> Unit,
) {
  val remainingByStart = existing.associateByTo(linkedMapOf(), entityStartSelector)
  val unmatchedTargets = mutableListOf<T>()
  val toSave = mutableListOf<E>()

  for (target in targets) {
    val match = remainingByStart.remove(targetStartSelector(target))
    if (match != null) {
      match.updateFrom(target)
      toSave.add(match)
    } else {
      unmatchedTargets.add(target)
    }
  }

  val unmatched = ArrayDeque(remainingByStart.values)
  for (target in unmatchedTargets) {
    val recycled = unmatched.removeFirstOrNull()
    if (recycled != null) {
      updateFrom(recycled, target)
      toSave.add(recycled)
    } else {
      toSave.add(createEntity(target))
    }
  }

  if (unmatched.isNotEmpty()) {
    deleteAll(unmatched)
  }
  saveAll(toSave)
}
