package uk.gov.justice.hmpps.architecture.prison

import com.structurizr.model.Model
import com.structurizr.model.Container
import com.structurizr.model.SoftwareSystem

class NOMIS(model: Model) {
  val system: SoftwareSystem
  val db: Container
  val elite2api: Container

  init {
    val cloudPlatform = model.getDeploymentNodeWithName("Cloud Platform")
    val sqs = cloudPlatform.getDeploymentNodeWithName("SQS")
    val sns = cloudPlatform.getDeploymentNodeWithName("SNS")
    val kubernetes = cloudPlatform.getDeploymentNodeWithName("Kubernetes")

    system = model.addSoftwareSystem("NOMIS", """
    National Offender Management Information System,
    the case management system for offender data in use in custody - both public and private prisons
    """.trimIndent())

    db = system.addContainer("NOMIS database", null, "Oracle").apply {
      addTags(DELIUS.DATABASE_TAG)
    }

    system.addContainer("NOMIS Web Application",
        "Web Application fronting the NOMIS DB - used by Digital Prison team applications and services",
        "Weblogic App Sever").apply {
      uses(db, "JDBC")
    }

    elite2api = system.addContainer("Elite2 API",
        "API over the NOMIS DB used by Digital Prison team applications and services", "Java")
        .apply {
          setUrl("https://github.com/ministryofjustice/elite2-api")
          addTags("database")
          uses(db, "JDBC")
        }

    val elasticSearchStore = system.addContainer("ElasticSearch store",
        "Data store for feedback collection, and indexing for Drupal CMS content", "ElasticSearch")
        .apply {
          addTags(DELIUS.DATABASE_TAG)
          addTags(DELIUS.SOFTWARE_AS_A_SERVICE_TAG)
          cloudPlatform.add(this)
        }

    system.addContainer("PrisonerSearch", "API over the NOMIS prisoner data held in Elasticsearch",
        "Java").apply {
      uses(elasticSearchStore, "Queries prisoner data from NOMIS Elasticsearch Index")
      cloudPlatform.add(this)
      kubernetes.add(this)
    }

    system.addContainer("Custody API (Deprecated)",
        "(Deprecated) Offender API.  The service provides REST access to the Nomis Oracle DB offender information. Deprecated - please use Elite2 API instead.",
        null).apply {
      setUrl("https://github.com/ministryofjustice/custody-api")
      addTags("deprecated")
      uses(db, "JDBC")
    }

    system.addContainer("NOMIS API (Deprecated)",
        "(Deprecated) REST API for NOMIS which connects to Oracle DB. Deprecated - please use " + elite2api.getName() + " instead.",
        null).apply {
      setUrl("https://github.com/ministryofjustice/nomis-api")
      addTags("deprecated")
      uses(db, "JDBC")
    }

    system.addContainer("Offender Events",
        "Publishes Events about offender change to Pub / Sub Topics.", "Java").apply {
      setUrl("https://github.com/ministryofjustice/offender-events")
      uses(db, "JDBC")
      cloudPlatform.add(this)
      sqs.add(this)
      sns.add(this)
      kubernetes.add(this)
    }

  }
}
