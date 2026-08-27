package uk.gov.justice.digital.hmpps.personlocationapi.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.QueryHint
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.envers.Audited
import org.hibernate.jpa.HibernateHints
import org.hibernate.type.SqlTypes
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.QueryHints
import uk.gov.justice.digital.hmpps.personlocationapi.event.DomainEvent
import java.util.UUID

@Audited
@Entity
@Table(name = "hmpps_domain_event")
class HmppsDomainEvent(
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "event")
  var event: DomainEvent<*>,

  @Column(name = "entity_id")
  var entityId: UUID,

  @Id
  @Column(name = "id", nullable = false)
  var id: UUID = event.id,
) {
  @Version
  var version: Int? = null

  @Column(name = "event_type")
  var eventType: String = event.eventType

  var published: Boolean = false
}

interface HmppsDomainEventRepository : JpaRepository<HmppsDomainEvent, UUID> {
  @QueryHints(value = [QueryHint(name = HibernateHints.HINT_NATIVE_LOCK_MODE, value = "UPGRADE-SKIPLOCKED")])
  fun findByPublishedIsFalseOrderById(pageable: Pageable): List<HmppsDomainEvent>
}
