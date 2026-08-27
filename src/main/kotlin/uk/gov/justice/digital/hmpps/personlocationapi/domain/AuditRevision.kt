package uk.gov.justice.digital.hmpps.personlocationapi.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.envers.EntityTrackingRevisionListener
import org.hibernate.envers.RevisionEntity
import org.hibernate.envers.RevisionNumber
import org.hibernate.envers.RevisionTimestamp
import org.hibernate.envers.RevisionType
import org.hibernate.type.SqlTypes
import uk.gov.justice.digital.hmpps.personlocationapi.context.RequestContext
import java.time.LocalDateTime

@Entity
@Table(name = "audit_revision")
@RevisionEntity(AuditRevisionEntityListener::class)
@SequenceGenerator(name = "audit_revision_id_seq", sequenceName = "audit_revision_id_seq", allocationSize = 1)
class AuditRevision {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "audit_revision_id_seq")
  @RevisionNumber
  var id: Long? = null

  // must be called timestamp for EnversRevisionRepositoryImpl
  @RevisionTimestamp
  var timestamp: LocalDateTime? = null

  var username: String? = null

  @Enumerated(EnumType.STRING)
  var source: DataSource? = null

  var reason: String? = null

  @Column(name = "caseload_id")
  var caseloadId: String? = null

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "affected_entities", nullable = false)
  var affectedEntities: MutableSet<String> = sortedSetOf(String.CASE_INSENSITIVE_ORDER)
}

class AuditRevisionEntityListener : EntityTrackingRevisionListener {
  override fun newRevision(revision: Any?) {
    (revision as AuditRevision).apply {
      val context: RequestContext = RequestContext.get()
      timestamp = context.requestAt
      username = context.username
      source = context.source
      reason = context.reason
      caseloadId = context.caseloadId
    }
  }

  override fun entityChanged(
    entityClass: Class<*>,
    entityName: String,
    entityId: Any,
    revisionType: RevisionType,
    revision: Any,
  ) {
    (revision as AuditRevision).apply {
      affectedEntities.add(entityClass.simpleName)
    }
  }
}
